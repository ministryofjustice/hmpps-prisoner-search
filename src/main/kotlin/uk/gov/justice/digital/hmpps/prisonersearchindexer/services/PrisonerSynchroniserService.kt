package uk.gov.justice.digital.hmpps.prisonersearchindexer.services

import arrow.core.Either
import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearchindexer.config.TelemetryEvents
import uk.gov.justice.digital.hmpps.prisonersearchindexer.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonersearchindexer.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearchindexer.model.SyncIndex
import uk.gov.justice.digital.hmpps.prisonersearchindexer.model.translate
import uk.gov.justice.digital.hmpps.prisonersearchindexer.repository.PrisonerRepository
import uk.gov.justice.digital.hmpps.prisonersearchindexer.services.dto.nomis.OffenderBooking

@Service
class PrisonerSynchroniserService(
  private val prisonerRepository: PrisonerRepository,
  private val telemetryClient: TelemetryClient,
  private val nomisService: NomisService,
  private val restrictedPatientService: RestrictedPatientService,
  private val incentivesService: IncentivesService,
) {

  // called when prisoner updated or manual prisoner index required
  internal fun reindex(prisonerNumber: String, vararg indexes: SyncIndex): Either<PrisonerError, Prisoner> =
    nomisService.getOffender(prisonerNumber).map { ob ->
      val incentiveLevel = runCatching { getIncentive(ob) }
      val restrictedPatient = runCatching { getRestrictedPatient(ob) }

      val existingPrisoner = indexes.map { prisonerRepository.get(prisonerNumber, it) }.firstOrNull()

      val prisoner = Prisoner().translate(
        existingPrisoner = existingPrisoner,
        ob = ob,
        incentiveLevel = incentiveLevel,
        restrictedPatientData = restrictedPatient,
      )
      indexes.map { index -> prisonerRepository.save(prisoner, index) }.also {
        telemetryClient.trackEvent(TelemetryEvents.PRISONER_UPDATED, mapOf("prisonerNumber" to prisoner.prisonerNumber!!))
      }
      incentiveLevel.onFailure { throw it }
      restrictedPatient.onFailure { throw it }
      prisoner
    }

  // called when index being built from scratch.  In this scenario we fail early since the index isn't in use anyway
  // we don't need to write what we have so far for the prisoner to it.
  internal fun index(prisonerNumber: String, vararg indexes: SyncIndex): Either<PrisonerError, Prisoner> =
    nomisService.getOffender(prisonerNumber).map { ob ->
      val prisoner = Prisoner().translate(
        ob = ob,
        incentiveLevel = Result.success(getIncentive(ob)),
        restrictedPatientData = Result.success(getRestrictedPatient(ob)),
      )
      indexes.map { index -> prisonerRepository.save(prisoner, index) }
      prisoner
    }

  private fun getRestrictedPatient(ob: OffenderBooking) =
    if (ob.assignedLivingUnit?.agencyId == "OUT") {
      restrictedPatientService.getRestrictedPatient(ob.offenderNo)
    } else {
      null
    }

  private fun getIncentive(ob: OffenderBooking) = ob.bookingId?.let { b -> incentivesService.getCurrentIncentive(b) }
}
