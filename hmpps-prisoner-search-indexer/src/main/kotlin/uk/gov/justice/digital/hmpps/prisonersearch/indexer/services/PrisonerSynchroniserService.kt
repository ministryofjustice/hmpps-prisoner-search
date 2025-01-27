package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.common.dps.IncentiveLevel
import uk.gov.justice.digital.hmpps.prisonersearch.common.dps.RestrictedPatient
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.CurrentIncentive
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.SyncIndex
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.setLocationDescription
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.setRestrictedPatientFields
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.toCurrentIncentive
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.translate
import uk.gov.justice.digital.hmpps.prisonersearch.common.nomis.OffenderBooking
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.TelemetryEvents
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.TelemetryEvents.PRISONER_OPENSEARCH_NO_CHANGE
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.trackPrisonerEvent
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.repository.PrisonerDifferencesLabel
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.repository.PrisonerRepository
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.AlertsUpdatedEventService
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.HmppsDomainEventEmitter
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.PrisonerMovementsEventService

@Service
class PrisonerSynchroniserService(
  private val prisonerRepository: PrisonerRepository,
  private val telemetryClient: TelemetryClient,
  private val restrictedPatientService: RestrictedPatientService,
  private val incentivesService: IncentivesService,
  private val prisonerDifferenceService: PrisonerDifferenceService,
  private val prisonerMovementsEventService: PrisonerMovementsEventService,
  private val alertsUpdatedEventService: AlertsUpdatedEventService,
  private val domainEventEmitter: HmppsDomainEventEmitter,
) {
  companion object {
    private val log = org.slf4j.LoggerFactory.getLogger(this::class.java)
  }

  // called when prisoner updated or manual prisoner index required
  internal fun reindex(ob: OffenderBooking, indices: List<SyncIndex>, eventType: String): Prisoner {
    val incentiveLevel = runCatching { getIncentive(ob) }
    val restrictedPatient = runCatching { getRestrictedPatient(ob) }

    val existingPrisoner = prisonerRepository.get(ob.offenderNo, indices)

    val prisoner = Prisoner().translate(
      existingPrisoner = existingPrisoner,
      ob = ob,
      incentiveLevel = incentiveLevel,
      restrictedPatientData = restrictedPatient,
    )
    // only save to open search if we encounter any differences
    if (prisonerDifferenceService.hasChanged(existingPrisoner, prisoner)) {
      indices.map { index -> prisonerRepository.save(prisoner, index) }
      if (prisonerDifferenceService.handleDifferences(existingPrisoner, ob, prisoner, eventType)) {
        prisonerMovementsEventService.generateAnyEvents(existingPrisoner, prisoner, ob)
        alertsUpdatedEventService.generateAnyEvents(existingPrisoner, prisoner)
      }
    } else {
      telemetryClient.trackPrisonerEvent(
        PRISONER_OPENSEARCH_NO_CHANGE,
        prisonerNumber = ob.offenderNo,
        bookingId = ob.bookingId,
        eventType = eventType,
      )
    }

    incentiveLevel.onFailure { throw it }
    restrictedPatient.onFailure { throw it }
    return prisoner
  }

  internal fun reindexUpdate(ob: OffenderBooking, eventType: String): Boolean {
    val summary = prisonerRepository.getSummary(ob.offenderNo, SyncIndex.RED)
    val isChanged = summary?.run {
      val prisoner = Prisoner().translate(
        existingPrisoner = summary.prisoner,
        ob = ob,
        incentiveLevel = Result.failure(Exception()),
        restrictedPatientData = Result.failure(Exception()),
      )
      // opensearch reports if there are any differences
      val isUpdated = prisonerRepository.updatePrisoner(ob.offenderNo, prisoner, SyncIndex.RED, summary)
      if (isUpdated) {
        if (summary.prisoner == null) {
          // cannot happen hopefully!
          log.warn("Prisoner not found in RED index for {}", ob.offenderNo)
          throw PrisonerNotFoundException(ob.offenderNo)
        }
        prisonerDifferenceService.getDifferencesByCategory(summary.prisoner, prisoner)
          .also {
            domainEventEmitter.emitPrisonerDifferenceEvent(ob.offenderNo, it, red = true)
          }

        telemetryClient.trackPrisonerEvent(
          TelemetryEvents.RED_PRISONER_UPDATED,
          prisonerNumber = ob.offenderNo,
          bookingId = ob.bookingId,
          eventType = eventType,
        )
        // If the current booking id changed, some domain data needs updating too, retrieving using the new booking id
        if (summary.prisoner.bookingId?.toLong() != ob.bookingId) {
          reindexIncentive(ob.offenderNo, SyncIndex.RED, eventType)
          reindexRestrictedPatient(ob.offenderNo, ob, SyncIndex.RED, eventType)
        }
        prisonerMovementsEventService.generateAnyEvents(summary.prisoner, prisoner, ob, red = true)
        alertsUpdatedEventService.generateAnyEvents(summary.prisoner, prisoner, red = true)
      } else {
        telemetryClient.trackPrisonerEvent(
          TelemetryEvents.RED_PRISONER_OPENSEARCH_NO_CHANGE,
          prisonerNumber = ob.offenderNo,
          bookingId = ob.bookingId,
          eventType = eventType,
        )
      }
      isUpdated
    } ?: run {
      // Need to create, which means calling all domain data too.
      // NOTE if multiple messages are received for the same prisoner at the same time
      // the create operation could be attempted multiple times

      val prisoner = Prisoner().translate(
        existingPrisoner = null,
        ob = ob,
        incentiveLevel = runCatching { getIncentive(ob) },
        restrictedPatientData = runCatching { getRestrictedPatient(ob) },
      )
      prisonerRepository.createPrisoner(prisoner, SyncIndex.RED)
      // If prisoner already exists in opensearch, an exception is thrown (same as for version conflict with update)

      domainEventEmitter.emitPrisonerCreatedEvent(ob.offenderNo, true)
      prisonerMovementsEventService.generateAnyEvents(null, prisoner, ob, red = true)
      alertsUpdatedEventService.generateAnyEvents(null, prisoner, red = true)
      true
    }

    return isChanged
  }

  internal fun reindexIncentive(prisonerNo: String, index: SyncIndex, eventType: String) = prisonerRepository.getSummary(prisonerNo, index)
    ?.run {
      val bookingId = this.prisoner?.bookingId?.toLong() ?: throw PrisonerNotFoundException(prisonerNo)
      val incentiveLevel = incentivesService.getCurrentIncentive(bookingId)
      val newLevel: CurrentIncentive? = incentiveLevel.toCurrentIncentive()

      prisonerRepository.updateIncentive(prisonerNo, newLevel, index, this)
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
            prisonerDifferenceService.generateDiffEvent(this.prisoner, prisonerNo, newPrisoner, true)
          }
        }
    }

  internal fun reindexRestrictedPatient(prisonerNo: String, ob: OffenderBooking, index: SyncIndex, eventType: String) = prisonerRepository.getSummary(prisonerNo, index)
    ?.run {
      if (this.prisoner == null) {
        log.warn("Prisoner not found in RED index for {}", prisonerNo)
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
        index,
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
            prisonerDifferenceService.generateDiffEvent(existingPrisoner, prisonerNo, this.prisoner, true)
          }
        }
    }

  // called when index being built from scratch.  In this scenario we fail early since the index isn't in use anyway
  // we don't need to write what we have so far for the prisoner to it.
  internal fun index(ob: OffenderBooking, vararg indexes: SyncIndex): Prisoner = translate(ob).also {
    indexes.map { index -> prisonerRepository.save(it, index) }
    prisonerRepository.save(it, SyncIndex.RED)
  }

  internal fun compareAndMaybeIndex(
    ob: OffenderBooking,
    incentiveLevelData: Result<IncentiveLevel?>,
    restrictedPatientData: Result<RestrictedPatient?>,
    indices: List<SyncIndex>,
    label: PrisonerDifferencesLabel,
  ) {
    val existingPrisoner = prisonerRepository.get(ob.offenderNo, indices)

    val prisoner = Prisoner().translate(
      existingPrisoner = existingPrisoner,
      ob = ob,
      incentiveLevel = incentiveLevelData,
      restrictedPatientData = restrictedPatientData,
    )
    if (prisonerDifferenceService.hasChanged(existingPrisoner, prisoner)) {
      prisonerDifferenceService.reportDiffTelemetry(existingPrisoner, prisoner, label)

      indices.map { prisonerRepository.save(prisoner, it) }
      if (label == PrisonerDifferencesLabel.GREEN_BLUE) {
        if (prisonerDifferenceService.handleDifferences(existingPrisoner, ob, prisoner, "REFRESH")) {
          prisonerMovementsEventService.generateAnyEvents(existingPrisoner, prisoner, ob)
          alertsUpdatedEventService.generateAnyEvents(existingPrisoner, prisoner)
        }
      } else {
        prisonerMovementsEventService.generateAnyEvents(existingPrisoner, prisoner, ob, red = true)
        alertsUpdatedEventService.generateAnyEvents(existingPrisoner, prisoner, red = true)
      }
    }
  }

  internal fun getDomainData(ob: OffenderBooking): Pair<Result<IncentiveLevel?>, Result<RestrictedPatient?>> = Pair(Result.success(getIncentive(ob)), Result.success(getRestrictedPatient(ob)))

  internal fun translate(ob: OffenderBooking): Prisoner = Prisoner().translate(
    ob = ob,
    incentiveLevel = Result.success(getIncentive(ob)),
    restrictedPatientData = Result.success(getRestrictedPatient(ob)),
  )

  fun delete(prisonerNumber: String) = prisonerRepository.delete(prisonerNumber).also {
    prisonerRepository.delete(prisonerNumber, SyncIndex.RED)
    telemetryClient.trackPrisonerEvent(TelemetryEvents.PRISONER_REMOVED, prisonerNumber)
  }

  private fun getRestrictedPatient(ob: OffenderBooking) = ob.takeIf { it.assignedLivingUnit?.agencyId == "OUT" }?.let {
    restrictedPatientService.getRestrictedPatient(it.offenderNo)
  }

  private fun getIncentive(ob: OffenderBooking) = ob.bookingId?.let { b -> incentivesService.getCurrentIncentive(b) }
}
