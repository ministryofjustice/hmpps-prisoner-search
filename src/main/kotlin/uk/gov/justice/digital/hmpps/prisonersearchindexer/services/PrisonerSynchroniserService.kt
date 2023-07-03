package uk.gov.justice.digital.hmpps.prisonersearchindexer.services

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.right
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

  internal fun synchronisePrisoner(prisonerNumber: String, vararg indexes: SyncIndex): Either<PrisonerError, Prisoner> =
    nomisService.getOffender(prisonerNumber)
      .map { convertToPrisoner(it) }
      .flatMap {
        indexes.map { index -> prisonerRepository.save(it, index) }
        it.right()
          .also {
            telemetryClient.trackEvent(TelemetryEvents.PRISONER_UPDATED, mapOf("prisonerNumber" to prisonerNumber))
          }
      }

  private fun convertToPrisoner(ob: OffenderBooking): Prisoner {
    val incentiveLevel = ob.bookingId?.let { incentivesService.getCurrentIncentive(it) }
    val restrictedPatient = if (ob.assignedLivingUnit?.agencyId == "OUT") {
      restrictedPatientService.getRestrictedPatient(ob.offenderNo)?.let {
        RestrictedPatient(
          supportingPrisonId = it.supportingPrison.agencyId,
          dischargedHospital = it.hospitalLocation,
          dischargeDate = it.dischargeTime.toLocalDate(),
          dischargeDetails = it.commentText,
        )
      }
    } else {
      null
    }
    return Prisoner().translate(
      ob = ob,
      incentiveLevel = Result.success(incentiveLevel),
      restrictedPatientData = restrictedPatient,
    )
  }
}
