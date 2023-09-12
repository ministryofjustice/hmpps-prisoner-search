package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.SyncIndex
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.TelemetryEvents
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.trackPrisonerEvent
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.model.translate
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.repository.PrisonerRepository
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.dto.nomis.OffenderBooking

@Service
class PrisonerSynchroniserService(
  private val prisonerRepository: PrisonerRepository,
  private val telemetryClient: TelemetryClient,
  private val restrictedPatientService: RestrictedPatientService,
  private val incentivesService: IncentivesService,
  private val prisonerDifferenceService: PrisonerDifferenceService,
) {

  // called when prisoner updated or manual prisoner index required
  internal fun reindex(ob: OffenderBooking, indices: List<SyncIndex>): Prisoner {
    val incentiveLevel = runCatching { getIncentive(ob) }
    val restrictedPatient = runCatching { getRestrictedPatient(ob) }

    val existingPrisoner = prisonerRepository.get(ob.offenderNo, indices)

    val prisoner = Prisoner().translate(
      existingPrisoner = existingPrisoner,
      ob = ob,
      incentiveLevel = incentiveLevel,
      restrictedPatientData = restrictedPatient,
    )
    indices.map { index -> prisonerRepository.save(prisoner, index) }

    prisonerDifferenceService.handleDifferences(existingPrisoner, ob, prisoner)

    incentiveLevel.onFailure { throw it }
    restrictedPatient.onFailure { throw it }
    return prisoner
  }

  // called when index being built from scratch.  In this scenario we fail early since the index isn't in use anyway
  // we don't need to write what we have so far for the prisoner to it.
  internal fun index(ob: OffenderBooking, vararg indexes: SyncIndex): Prisoner =
    translate(ob).also {
      indexes.map { index -> prisonerRepository.save(it, index) }
    }

  internal fun compareAndMaybeIndex(ob: OffenderBooking, indices: List<SyncIndex>): Prisoner {
    val incentiveLevel = getIncentive(ob)
    val restrictedPatient = getRestrictedPatient(ob)

    val existingPrisoner = prisonerRepository.get(ob.offenderNo, indices)

    val prisoner = Prisoner().translate(
      existingPrisoner = existingPrisoner,
      ob = ob,
      incentiveLevel = Result.success(incentiveLevel),
      restrictedPatientData = Result.success(restrictedPatient),
    )
    if (prisonerDifferenceService.prisonerHasChanged(existingPrisoner, prisoner)) {
      prisonerDifferenceService.reportDiffTelemetry(existingPrisoner, prisoner)

      indices.map { prisonerRepository.save(prisoner, it) }

      prisonerDifferenceService.handleDifferences(existingPrisoner, ob, prisoner)
    }
    return prisoner
  }

  internal fun translate(ob: OffenderBooking): Prisoner =
    Prisoner().translate(
      ob = ob,
      incentiveLevel = Result.success(getIncentive(ob)),
      restrictedPatientData = Result.success(getRestrictedPatient(ob)),
    )

  fun delete(prisonerNumber: String) =
    prisonerRepository.delete(prisonerNumber).also {
      telemetryClient.trackPrisonerEvent(TelemetryEvents.PRISONER_REMOVED, prisonerNumber)
    }

  private fun getRestrictedPatient(ob: OffenderBooking) =
    ob.takeIf { it.assignedLivingUnit?.agencyId == "OUT" }?.let {
      restrictedPatientService.getRestrictedPatient(it.offenderNo)
    }

  private fun getIncentive(ob: OffenderBooking) = ob.bookingId?.let { b -> incentivesService.getCurrentIncentive(b) }
}
