package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.CurrentIncentive
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.SyncIndex
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.toCurrentIncentive
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.translate
import uk.gov.justice.digital.hmpps.prisonersearch.common.nomis.OffenderBooking
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.TelemetryEvents
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.TelemetryEvents.PRISONER_OPENSEARCH_NO_CHANGE
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.trackPrisonerEvent
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.repository.PrisonerRepository

@Service
class PrisonerSynchroniserService(
  private val prisonerRepository: PrisonerRepository,
  private val telemetryClient: TelemetryClient,
  private val restrictedPatientService: RestrictedPatientService,
  private val incentivesService: IncentivesService,
  private val prisonerDifferenceService: PrisonerDifferenceService,
) {

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
      prisonerDifferenceService.handleDifferences(existingPrisoner, ob, prisoner, eventType)
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
      val restrictedPatient = runCatching { getRestrictedPatient(ob) }

      val prisoner = Prisoner().translate(
        existingPrisoner = summary.prisoner,
        ob = ob,
        incentiveLevel = Result.failure(Exception()),
        restrictedPatientData = restrictedPatient,
      )
      // opensearch reports if there are any differences
      val isUpdated = prisonerRepository.updatePrisoner(ob.offenderNo, prisoner, SyncIndex.RED, summary)
      if (isUpdated) {
        // prisonerDifferenceService.handleDifferences(summary.prisoner, ob, prisoner, eventType)
        // Already recorded and events generated in existing index update
        telemetryClient.trackPrisonerEvent(
          TelemetryEvents.RED_PRISONER_UPDATED,
          prisonerNumber = ob.offenderNo,
          bookingId = ob.bookingId,
          eventType = eventType,
        )
      } else {
        telemetryClient.trackPrisonerEvent(
          TelemetryEvents.RED_PRISONER_OPENSEARCH_NO_CHANGE,
          prisonerNumber = ob.offenderNo,
          bookingId = ob.bookingId,
          eventType = eventType,
        )
      }
      restrictedPatient.onFailure { throw it }
      isUpdated
    } ?: run {
      // Need to create, which means calling all domain data too
      reindex(ob, listOf(SyncIndex.RED), eventType)
      true
    }

    return isChanged
  }

  internal fun reindexIncentive(prisonerNo: String, index: SyncIndex, eventType: String) =
    prisonerRepository.getSummary(prisonerNo, index)
      ?.run {
        val bookingId = this.prisoner?.bookingId?.toLong() ?: throw PrisonerNotFoundException(prisonerNo)
        val incentiveLevel = incentivesService.getCurrentIncentive(bookingId)
        val newLevel: CurrentIncentive = incentiveLevel.toCurrentIncentive()!!

        if (prisonerRepository.updateIncentive(prisonerNo, newLevel, index, this)) {
          telemetryClient.trackPrisonerEvent(
            TelemetryEvents.INCENTIVE_UPDATED,
            prisonerNumber = prisonerNo,
            bookingId = bookingId,
            eventType = eventType,
          )
        } else {
          telemetryClient.trackPrisonerEvent(
            TelemetryEvents.INCENTIVE_OPENSEARCH_NO_CHANGE,
            prisonerNumber = prisonerNo,
            bookingId = bookingId,
            eventType = eventType,
          )
        }
      }

  // called when index being built from scratch.  In this scenario we fail early since the index isn't in use anyway
  // we don't need to write what we have so far for the prisoner to it.
  internal fun index(ob: OffenderBooking, vararg indexes: SyncIndex): Prisoner =
    translate(ob).also {
      indexes.map { index -> prisonerRepository.save(it, index) }
      prisonerRepository.save(it, SyncIndex.RED)
    }

  internal fun compareAndMaybeIndex(ob: OffenderBooking, indices: List<SyncIndex>, label: String) {
    val incentiveLevel = getIncentive(ob)
    val restrictedPatient = getRestrictedPatient(ob)

    val existingPrisoner = prisonerRepository.get(ob.offenderNo, indices)

    val prisoner = Prisoner().translate(
      existingPrisoner = existingPrisoner,
      ob = ob,
      incentiveLevel = Result.success(incentiveLevel),
      restrictedPatientData = Result.success(restrictedPatient),
    )
    if (prisonerDifferenceService.hasChanged(existingPrisoner, prisoner)) {
      prisonerDifferenceService.reportDiffTelemetry(existingPrisoner, prisoner, label)

      indices.map { prisonerRepository.save(prisoner, it) }
      prisonerDifferenceService.handleDifferences(existingPrisoner, ob, prisoner, "REFRESH")
    }
  }

  internal fun translate(ob: OffenderBooking): Prisoner =
    Prisoner().translate(
      ob = ob,
      incentiveLevel = Result.success(getIncentive(ob)),
      restrictedPatientData = Result.success(getRestrictedPatient(ob)),
    )

  fun delete(prisonerNumber: String) =
    prisonerRepository.delete(prisonerNumber).also {
      prisonerRepository.delete(prisonerNumber, SyncIndex.RED)
      telemetryClient.trackPrisonerEvent(TelemetryEvents.PRISONER_REMOVED, prisonerNumber)
    }

  private fun getRestrictedPatient(ob: OffenderBooking) =
    ob.takeIf { it.assignedLivingUnit?.agencyId == "OUT" }?.let {
      restrictedPatientService.getRestrictedPatient(it.offenderNo)
    }

  private fun getIncentive(ob: OffenderBooking) = ob.bookingId?.let { b -> incentivesService.getCurrentIncentive(b) }
}
