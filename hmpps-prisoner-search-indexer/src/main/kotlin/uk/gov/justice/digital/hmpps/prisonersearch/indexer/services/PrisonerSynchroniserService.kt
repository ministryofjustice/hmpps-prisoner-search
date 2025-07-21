package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.common.dps.Alert
import uk.gov.justice.digital.hmpps.prisonersearch.common.dps.ComplexityOfNeed
import uk.gov.justice.digital.hmpps.prisonersearch.common.dps.IncentiveLevel
import uk.gov.justice.digital.hmpps.prisonersearch.common.dps.RestrictedPatient
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.CurrentIncentive
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.PrisonerAlert
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.TelemetryEvents
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.trackPrisonerEvent
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.model.isExpired
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.model.nomis.OffenderBooking
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.model.setLocationDescription
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.model.setRestrictedPatientFields
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.model.toCurrentIncentive
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.model.translate
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.repository.PrisonerDocumentSummary
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.repository.PrisonerRepository
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.AlertsUpdatedEventService
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.ConvictedStatusEventService
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.HmppsDomainEventEmitter
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.PrisonerMovementsEventService
import java.time.LocalDate
import kotlin.collections.map

@Service
class PrisonerSynchroniserService(
  private val prisonerRepository: PrisonerRepository,
  private val telemetryClient: TelemetryClient,
  private val restrictedPatientService: RestrictedPatientService,
  private val incentivesService: IncentivesService,
  private val alertsService: AlertsService,
  private val complexityOfNeedService: ComplexityOfNeedService,
  private val prisonRegisterService: PrisonRegisterService,
  private val prisonerDifferenceService: PrisonerDifferenceService,
  private val prisonerMovementsEventService: PrisonerMovementsEventService,
  private val alertsUpdatedEventService: AlertsUpdatedEventService,
  private val convictedStatusEventService: ConvictedStatusEventService,
  private val domainEventEmitter: HmppsDomainEventEmitter,
) {
  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  internal fun reindexUpdate(ob: OffenderBooking, eventType: String): Prisoner {
    val summary = prisonerRepository.getSummary(ob.offenderNo)
    val isChanged = summary?.run {
      assertPrisonerNo(ob.offenderNo)
      val prisoner = Prisoner().translate(
        existingPrisoner = summary.prisoner,
        ob = ob,
        incentiveLevel = Result.failure(Exception()),
        restrictedPatientData = Result.failure(Exception()),
        alerts = Result.failure(Exception()),
        complexityOfNeed = Result.failure(Exception()),
      )
      // opensearch reports if there are any differences
      val isUpdated = prisonerRepository.updatePrisoner(ob.offenderNo, prisoner, summary)
      if (isUpdated) {
        prisonerDifferenceService.generateDiffEvent(summary.prisoner, ob.offenderNo, prisoner)

        telemetryClient.trackPrisonerEvent(
          TelemetryEvents.PRISONER_UPDATED,
          prisonerNumber = ob.offenderNo,
          bookingId = ob.bookingId,
          eventType = eventType,
        )
        // If the current booking id changed, some domain data needs updating too, retrieving using the new booking id
        // If this fails (e.g. seq_no conflict), allow important events to be generated before failing
        val bookingChanged = summary.prisoner!!.bookingId?.toLong() != ob.bookingId
        val incentiveResult = runCatching {
          if (bookingChanged) {
            reindexIncentive(ob.offenderNo, eventType)
          }
        }
        val restrictedPatientResult = runCatching {
          if (bookingChanged) {
            reindexRestrictedPatient(ob.offenderNo, ob, eventType)
          }
        }

        prisonerMovementsEventService.generateAnyEvents(summary.prisoner, prisoner, ob)
        convictedStatusEventService.generateAnyEvents(summary.prisoner, prisoner)

        incentiveResult.onFailure { throw it }
        restrictedPatientResult.onFailure { throw it }
      } else {
        telemetryClient.trackPrisonerEvent(
          TelemetryEvents.PRISONER_OPENSEARCH_NO_CHANGE,
          prisonerNumber = ob.offenderNo,
          bookingId = ob.bookingId,
          eventType = eventType,
        )
      }
      prisoner
    } ?: run {
      // Need to create, which means calling all domain data too.
      // NOTE if multiple messages are received for the same prisoner at the same time
      // the create operation could be attempted multiple times

      val prisoner = Prisoner().translate(
        existingPrisoner = null,
        ob = ob,
        incentiveLevel = runCatching { getIncentive(ob) },
        restrictedPatientData = runCatching { getRestrictedPatient(ob) },
        alerts = runCatching { alertsService.getActiveAlertsForPrisoner(ob.offenderNo) },
        complexityOfNeed = runCatching { getComplexityOfNeed(ob) },
      )
      prisonerRepository.createPrisoner(prisoner)
      // If prisoner already exists in opensearch, an exception is thrown (same as for version conflict with update)

      domainEventEmitter.emitPrisonerCreatedEvent(ob.offenderNo)
      prisonerMovementsEventService.generateAnyEvents(null, prisoner, ob)
      convictedStatusEventService.generateAnyEvents(null, prisoner)

      telemetryClient.trackPrisonerEvent(
        TelemetryEvents.PRISONER_CREATED,
        prisonerNumber = ob.offenderNo,
        bookingId = ob.bookingId,
        eventType = eventType,
      )

      prisoner
    }

    return isChanged
  }

  internal fun reindexIncentive(prisonerNo: String, eventType: String) = prisonerRepository.getSummary(prisonerNo)
    ?.run {
      val bookingId = this.prisoner?.bookingId?.toLong() ?: throw PrisonerNotFoundException(prisonerNo)
      val incentiveLevel = incentivesService.getCurrentIncentive(bookingId)
      val newLevel: CurrentIncentive? = incentiveLevel.toCurrentIncentive()

      prisonerRepository.updateIncentive(prisonerNo, newLevel, this)
        .also { updated ->
          telemetryClient.trackPrisonerEvent(
            if (updated) {
              TelemetryEvents.INCENTIVE_UPDATED
            } else {
              TelemetryEvents.INCENTIVE_OPENSEARCH_NO_CHANGE
            },
            prisonerNumber = prisonerNo,
            bookingId = bookingId,
            eventType = eventType,
          )
          if (updated) {
            val newPrisoner = prisonerRepository.copyPrisoner(this.prisoner)
            newPrisoner.currentIncentive = newLevel
            prisonerDifferenceService.generateDiffEvent(this.prisoner, prisonerNo, newPrisoner)
          }
        }
    }

  internal fun reindexRestrictedPatient(prisonerNo: String, ob: OffenderBooking, eventType: String) = prisonerRepository.getSummary(prisonerNo)
    ?.run {
      assertPrisonerNo(prisonerNo)
      val existingPrisoner = prisonerRepository.copyPrisoner(this.prisoner!!)
      val restrictedPatient = getRestrictedPatient(ob)
      this.prisoner.setLocationDescription(restrictedPatient, ob)
      this.prisoner.setRestrictedPatientFields(restrictedPatient)

      prisonerRepository.updateRestrictedPatient(
        prisonerNo,
        restrictedPatient = restrictedPatient != null,
        this.prisoner.supportingPrisonId,
        this.prisoner.dischargedHospitalId,
        this.prisoner.dischargedHospitalDescription,
        this.prisoner.dischargeDate,
        this.prisoner.dischargeDetails,
        this.prisoner.locationDescription,
        this,
      )
        .also { updated ->
          telemetryClient.trackPrisonerEvent(
            if (updated) {
              TelemetryEvents.RESTRICTED_PATIENT_UPDATED
            } else {
              TelemetryEvents.RESTRICTED_PATIENT_OPENSEARCH_NO_CHANGE
            },
            prisonerNumber = prisonerNo,
            bookingId = ob.bookingId,
            eventType = eventType,
          )
          if (updated) {
            prisonerDifferenceService.generateDiffEvent(existingPrisoner, prisonerNo, this.prisoner)
          }
        }
    }

  internal fun reindexAlerts(prisonerNo: String, eventType: String) = prisonerRepository.getSummary(prisonerNo)
    ?.run {
      assertPrisonerNo(prisonerNo)
      val bookingId = this.prisoner!!.bookingId?.toLong()
      val now = LocalDate.now()
      val alerts = alertsService.getActiveAlertsForPrisoner(prisonerNo)?.map {
        PrisonerAlert(
          alertCode = it.alertCode.code,
          alertType = it.alertCode.alertTypeCode,
          // expired mapping logic is the same as for sync to Nomis:
          expired = it.isExpired(now),
          active = it.isActive,
        )
      }

      prisonerRepository.updateAlerts(prisonerNo, alerts, this)
        .also { updated ->
          telemetryClient.trackPrisonerEvent(
            if (updated) {
              TelemetryEvents.ALERTS_UPDATED
            } else {
              TelemetryEvents.ALERTS_OPENSEARCH_NO_CHANGE
            },
            prisonerNumber = prisonerNo,
            bookingId = bookingId,
            eventType = eventType,
          )
          if (updated) {
            prisonerDifferenceService.generateAlertDiffEvent(this.prisoner.alerts, prisonerNo, alerts)
            alertsUpdatedEventService.generateAnyEvents(this.prisoner.alerts, alerts, prisoner)
          }
        }
    }

  internal fun reindexComplexityOfNeed(prisonerNo: String, level: String?, eventType: String) = prisonerRepository.getSummary(prisonerNo)
    ?.run {
      assertPrisonerNo(prisonerNo)
      prisonerRepository.updateComplexityOfNeed(prisonerNo, level, this)
        .also { updated ->
          telemetryClient.trackPrisonerEvent(
            if (updated) {
              TelemetryEvents.COMPLEXITY_OF_NEED_UPDATED
            } else {
              TelemetryEvents.COMPLEXITY_OF_NEED_OPENSEARCH_NO_CHANGE
            },
            prisonerNumber = prisonerNo,
            null,
            eventType = eventType,
          )
        }
    }

  internal fun reindexComplexityOfNeedWithGet(ob: OffenderBooking, eventType: String) {
    val level = getComplexityOfNeed(ob)?.level
    reindexComplexityOfNeed(ob.offenderNo, level, eventType)
  }

  private fun PrisonerDocumentSummary.assertPrisonerNo(prisonerNo: String) {
    if (this.prisoner == null) {
      log.warn("Prisoner not found in index for {}", prisonerNo)
      throw PrisonerNotFoundException(prisonerNo)
    }
  }

  internal fun index(ob: OffenderBooking): Prisoner = translate(ob).also {
    prisonerRepository.save(it)
  }

  internal fun compareAndMaybeIndex(
    ob: OffenderBooking,
    incentiveLevelData: Result<IncentiveLevel?>,
    restrictedPatientData: Result<RestrictedPatient?>,
    alerts: Result<List<Alert>?>,
    complexityOfNeed: Result<ComplexityOfNeed?>,
  ) {
    val existingPrisoner = prisonerRepository.get(ob.offenderNo)

    val prisoner = Prisoner().translate(
      existingPrisoner = existingPrisoner,
      ob = ob,
      incentiveLevel = incentiveLevelData,
      restrictedPatientData = restrictedPatientData,
      alerts = alerts,
      complexityOfNeed = complexityOfNeed,
    )
    if (prisonerDifferenceService.hasChanged(existingPrisoner, prisoner)) {
      prisonerDifferenceService.reportDiffTelemetry(existingPrisoner, prisoner)

      prisonerRepository.save(prisoner)

      prisonerDifferenceService.generateDiffEvent(existingPrisoner, ob.offenderNo, prisoner)
      alertsUpdatedEventService.generateAnyEvents(existingPrisoner, prisoner)
      prisonerMovementsEventService.generateAnyEvents(existingPrisoner, prisoner, ob)
      convictedStatusEventService.generateAnyEvents(existingPrisoner, prisoner)
    }
  }

  fun refresh(ob: OffenderBooking) {
    compareAndMaybeIndex(
      ob,
      Result.success(getIncentive(ob)),
      Result.success(getRestrictedPatient(ob)),
      Result.success(alertsService.getActiveAlertsForPrisoner(ob.offenderNo)),
      Result.success(getComplexityOfNeed(ob)),
    )
  }

  internal fun translate(ob: OffenderBooking): Prisoner = Prisoner().translate(
    ob = ob,
    incentiveLevel = Result.success(getIncentive(ob)),
    restrictedPatientData = Result.success(getRestrictedPatient(ob)),
    alerts = Result.success(alertsService.getActiveAlertsForPrisoner(ob.offenderNo)),
    complexityOfNeed = Result.success(getComplexityOfNeed(ob)),
  )

  fun delete(prisonerNumber: String): Boolean {
    val deleted = prisonerRepository.delete(prisonerNumber)
    telemetryClient.trackPrisonerEvent(
      if (deleted) {
        TelemetryEvents.PRISONER_REMOVED
      } else {
        TelemetryEvents.PRISONER_OPENSEARCH_NO_CHANGE
      },
      prisonerNumber,
    )
    return deleted
  }

  private fun getRestrictedPatient(ob: OffenderBooking) = ob.takeIf { it.assignedLivingUnit?.agencyId == "OUT" }?.let {
    restrictedPatientService.getRestrictedPatient(it.offenderNo)
  }

  private fun getIncentive(ob: OffenderBooking) = ob.bookingId?.let { b -> incentivesService.getCurrentIncentive(b) }

  private fun getComplexityOfNeed(ob: OffenderBooking): ComplexityOfNeed? = if (isInFemalePrison(ob)) {
    complexityOfNeedService.getComplexityOfNeedForPrisoner(ob.offenderNo)
  } else {
    null
  }

  private fun isInFemalePrison(ob: OffenderBooking): Boolean {
    val femalePrisons = prisonRegisterService.getAllPrisons()
      ?.filter { it.female }
      ?.map { it.prisonId }
    return ob.agencyId != null &&
      ob.agencyId != "OUT" &&
      // Failsafe - if prison register not available, make the CNL call regardless of gender
      (femalePrisons == null || femalePrisons.contains(ob.agencyId))
  }
}
