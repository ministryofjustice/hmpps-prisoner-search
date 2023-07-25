package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.SyncIndex
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners.IncentiveChangedMessage

@Service
class IndexListenerService(
  private val indexStatusService: IndexStatusService,
  private val prisonerSynchroniserService: PrisonerSynchroniserService,
  private val nomisService: NomisService,
) {
  fun incentiveChange(message: IncentiveChangedMessage) {
    log.info(
      "Incentive change: {} for prisoner {} with incentive id {}",
      message.description,
      message.additionalInformation.nomsNumber,
      message.additionalInformation.id,
    )
    indexPrisoner(message.additionalInformation.nomsNumber)
  }

  private fun indexPrisoner(prisonerNumber: String): Prisoner? =
    indexStatusService.getIndexStatus()
      .run {
        if (activeIndexesEmpty()) {
          log.info("Ignoring update of prisoner {} as no indexes were active", prisonerNumber)
          null
        } else {
          sync(prisonerNumber, activeIndexes())
        }
      }

  private fun sync(prisonerNumber: String, activeIndices: List<SyncIndex>): Prisoner? =
    nomisService.getOffender(prisonerNumber)?.run {
      prisonerSynchroniserService.reindex(this, activeIndices)
    } ?: null.also { log.warn("Sync requested for prisoner {} not found", prisonerNumber) }

  private companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
