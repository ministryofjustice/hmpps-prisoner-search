package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.common.dps.Alert
import uk.gov.justice.digital.hmpps.prisonersearch.common.dps.IncentiveLevel
import uk.gov.justice.digital.hmpps.prisonersearch.common.dps.RestrictedPatient
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.CurrentIncentive
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.PrisonerAlert
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.isExpired
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.setLocationDescription
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.setRestrictedPatientFields
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.toCurrentIncentive
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.translate
import uk.gov.justice.digital.hmpps.prisonersearch.common.nomis.OffenderBooking
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.TelemetryEvents
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.trackPrisonerEvent
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.repository.PrisonerRepository
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.AlertsUpdatedEventService
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.ConvictedStatusEventService
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.HmppsDomainEventEmitter
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.PrisonerMovementsEventService
import java.time.LocalDate

@Service
class PrisonerSynchroniserService(
  private val prisonerRepository: PrisonerRepository,
  private val telemetryClient: TelemetryClient,
  private val restrictedPatientService: RestrictedPatientService,
  private val incentivesService: IncentivesService,
  private val alertsService: AlertsService,
  private val prisonerDifferenceService: PrisonerDifferenceService,
  private val prisonerMovementsEventService: PrisonerMovementsEventService,
  private val alertsUpdatedEventService: AlertsUpdatedEventService,
  private val convictedStatusEventService: ConvictedStatusEventService,
  private val domainEventEmitter: HmppsDomainEventEmitter,
) {
  companion object {
    private val log = org.slf4j.LoggerFactory.getLogger(this::class.java)
  }

  internal fun reindexUpdate(ob: OffenderBooking, eventType: String): Prisoner {
    val summary = prisonerRepository.getSummary(ob.offenderNo)
    val isChanged = summary?.run {
      val prisoner = Prisoner().translate(
        existingPrisoner = summary.prisoner,
        ob = ob,
        incentiveLevel = Result.failure(Exception()),
        restrictedPatientData = Result.failure(Exception()),
        alerts = Result.failure(Exception()),
      )
      // opensearch reports if there are any differences
      val isUpdated = prisonerRepository.updatePrisoner(ob.offenderNo, prisoner, summary)
      if (isUpdated) {
        if (summary.prisoner == null) {
          // cannot happen hopefully!
          log.warn("Prisoner not found in index for {}", ob.offenderNo)
          throw PrisonerNotFoundException(ob.offenderNo)
        }
        prisonerDifferenceService.generateDiffEvent(summary.prisoner, ob.offenderNo, prisoner)

        telemetryClient.trackPrisonerEvent(
          TelemetryEvents.PRISONER_UPDATED,
          prisonerNumber = ob.offenderNo,
          bookingId = ob.bookingId,
          eventType = eventType,
        )
        // If the current booking id changed, some domain data needs updating too, retrieving using the new booking id
        if (summary.prisoner.bookingId?.toLong() != ob.bookingId) {
          reindexIncentive(ob.offenderNo, eventType)
          reindexRestrictedPatient(ob.offenderNo, ob, eventType)
        }
        prisonerMovementsEventService.generateAnyEvents(summary.prisoner, prisoner, ob)
        convictedStatusEventService.generateAnyEvents(summary.prisoner, prisoner)
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
        alerts = runCatching { getAlerts(ob.offenderNo) },
      )
      prisonerRepository.createPrisoner(prisoner)
      // If prisoner already exists in opensearch, an exception is thrown (same as for version conflict with update)

      domainEventEmitter.emitPrisonerCreatedEvent(ob.offenderNo)
      prisonerMovementsEventService.generateAnyEvents(null, prisoner, ob)
      convictedStatusEventService.generateAnyEvents(null, prisoner)
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
      if (this.prisoner == null) {
        log.warn("Prisoner not found in index for {}", prisonerNo)
        throw PrisonerNotFoundException(ob.offenderNo)
      }
      val existingPrisoner = prisonerRepository.copyPrisoner(this.prisoner)
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
      if (this.prisoner == null) {
        log.warn("Prisoner not found in index for {}", prisonerNo)
        throw PrisonerNotFoundException(prisonerNo)
      }
      val bookingId = this.prisoner.bookingId?.toLong()
      val now = LocalDate.now()
      val alerts = getAlerts(prisonerNo)?.map {
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

  internal fun index(ob: OffenderBooking): Prisoner = translate(ob).also {
    prisonerRepository.save(it)
  }

  internal fun compareAndMaybeIndex(
    ob: OffenderBooking,
    incentiveLevelData: Result<IncentiveLevel?>,
    restrictedPatientData: Result<RestrictedPatient?>,
    alerts: Result<List<Alert>?>,
  ) {
    val existingPrisoner = prisonerRepository.get(ob.offenderNo)

    val prisoner = Prisoner().translate(
      existingPrisoner = existingPrisoner,
      ob = ob,
      incentiveLevel = incentiveLevelData,
      restrictedPatientData = restrictedPatientData,
      alerts = alerts,
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
      Result.success(getAlerts(ob.offenderNo)),
    )
  }

  internal fun translate(ob: OffenderBooking): Prisoner = Prisoner().translate(
    ob = ob,
    incentiveLevel = Result.success(getIncentive(ob)),
    restrictedPatientData = Result.success(getRestrictedPatient(ob)),
    alerts = Result.success(getAlerts(ob.offenderNo)),
  )

  fun delete(prisonerNumber: String) {
    prisonerRepository.delete(prisonerNumber)
    telemetryClient.trackPrisonerEvent(TelemetryEvents.PRISONER_REMOVED, prisonerNumber)
  }

  private fun getRestrictedPatient(ob: OffenderBooking) = ob.takeIf { it.assignedLivingUnit?.agencyId == "OUT" }?.let {
    restrictedPatientService.getRestrictedPatient(it.offenderNo)
  }

  private fun getIncentive(ob: OffenderBooking) = ob.bookingId?.let { b -> incentivesService.getCurrentIncentive(b) }

  private fun getAlerts(prisonerNumber: String) = alertsService.getActiveAlertsForPrisoner(prisonerNumber)
}
