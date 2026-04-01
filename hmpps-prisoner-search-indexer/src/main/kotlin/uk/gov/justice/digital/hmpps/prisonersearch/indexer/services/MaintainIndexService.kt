package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.TelemetryEvents.PRISONER_NOT_FOUND
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.trackPrisonerEvent
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.repository.PrisonerRepository

@Service
class MaintainIndexService(
  private val prisonerSynchroniserService: PrisonerSynchroniserService,
  private val prisonerRepository: PrisonerRepository,
  private val nomisService: NomisService,
  private val telemetryClient: TelemetryClient,
) {
  fun indexPrisoner(prisonerNumber: String): Prisoner {
    val offenderBooking = nomisService.getOffender(prisonerNumber)
    return offenderBooking
      ?.also {
        val restrictedPatient = prisonerSynchroniserService.getRestrictedPatient(offenderBooking)
        prisonerSynchroniserService.reindexIncentive(prisonerNumber, "MAINTAIN")
        prisonerSynchroniserService.reindexRestrictedPatient(prisonerNumber, offenderBooking, restrictedPatient, "MAINTAIN")
        prisonerSynchroniserService.reindexAlerts(prisonerNumber, "MAINTAIN")
        prisonerSynchroniserService.reindexComplexityOfNeedWithGet(offenderBooking, restrictedPatient, "MAINTAIN")
      }
      ?.let {
        prisonerSynchroniserService.reindexUpdate(offenderBooking, "MAINTAIN")
      }
      ?: prisonerRepository.get(prisonerNumber)
        ?.apply {
          // Prisoner not in NOMIS, but found in indexes so remove
          prisonerSynchroniserService.delete(prisonerNumber)
        }
      ?: run {
        // not found in either NOMIS or index, so log and throw
        telemetryClient.trackPrisonerEvent(PRISONER_NOT_FOUND, prisonerNumber)
        throw PrisonerNotFoundException(prisonerNumber)
      }
  }
}

data class PrisonerPage(val page: Int, val pageSize: Int)
data class RootOffenderIdPage(val fromRootOffenderId: Long, val toRootOffenderId: Long)
