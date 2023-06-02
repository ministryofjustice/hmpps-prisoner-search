package uk.gov.justice.digital.hmpps.prisonersearchindexer.services

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.right
import com.microsoft.applicationinsights.TelemetryClient
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearchindexer.config.IndexBuildProperties
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
  indexBuildProperties: IndexBuildProperties,
) {
  private val pageSize = indexBuildProperties.pageSize

  private companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  internal fun synchronisePrisoner(prisonerNumber: String, vararg indexes: SyncIndex): Either<PrisonerError, Prisoner> =
    nomisService.getOffender(prisonerNumber)
      .map { convertToPrisoner(it) }
      .flatMap {
        indexes.map { index -> prisonerRepository.save(it, index) }
        it.right()
          .also {
            telemetryClient.trackEvent(TelemetryEvents.PRISONER_UPDATED.name, mapOf("prisonerNumber" to prisonerNumber))
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

  fun checkExistsAndReset(index: SyncIndex) {
    if (prisonerRepository.doesIndexExist(index)) {
      prisonerRepository.deleteIndex(index)
    }
    await untilCallTo { prisonerRepository.doesIndexExist(index) } matches { it == false }
    prisonerRepository.createIndex(index)
  }

  fun switchAliasIndex(index: SyncIndex) {
    prisonerRepository.switchAliasIndex(index)
  }

  fun splitAllPrisonersIntoChunks(): List<PrisonerPage> {
    val totalNumberOfPrisoners = nomisService.getOffendersIds(0, 1).totalRows
    log.info("Splitting $totalNumberOfPrisoners in to pages each of size $pageSize")
    return (1..totalNumberOfPrisoners step pageSize).toList()
      .map { PrisonerPage(it / pageSize, pageSize) }
      .also {
        telemetryClient.trackEvent(
          TelemetryEvents.POPULATE_PRISONER_PAGES.name,
          mapOf("totalNumberOfOffenders" to totalNumberOfPrisoners.toString(), "pageSize" to pageSize.toString()),
        )
      }
  }

  fun getAllPrisonerNumbersInPage(prisonerPage: PrisonerPage): List<OffenderId> =
    nomisService.getOffendersIds(prisonerPage.page, prisonerPage.pageSize).offenderIds ?: emptyList()
}

data class PrisonerPage(val page: Long, val pageSize: Long)
