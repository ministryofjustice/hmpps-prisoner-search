package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IndexStatus
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.IndexBuildProperties
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners.IndexRequestType
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners.IndexRequestType.REFRESH_ACTIVE_INDEX
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners.IndexRequestType.REFRESH_ACTIVE_PRISONER_PAGE
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners.IndexRequestType.REFRESH_INDEX
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners.IndexRequestType.REFRESH_PRISONER_PAGE

@Service
class RefreshIndexService(
  private val indexStatusService: IndexStatusService,
  private val indexQueueService: IndexQueueService,
  private val nomisService: NomisService,
  private val nomisPrisonerService: NomisPrisonerService,
  private val prisonerSynchroniserService: PrisonerSynchroniserService,
  indexBuildProperties: IndexBuildProperties,
) {
  private val pageSize = indexBuildProperties.pageSize

  fun startFullIndexRefresh(domainEvents: Boolean) {
    startIndexRefresh(REFRESH_INDEX, domainEvents)
  }

  fun startActiveIndexRefresh(domainEvents: Boolean) {
    startIndexRefresh(REFRESH_ACTIVE_INDEX, domainEvents)
  }

  private fun startIndexRefresh(type: IndexRequestType, domainEvents: Boolean) {
    val indexQueueStatus = indexQueueService.getIndexQueueStatus()
    return indexStatusService.getIndexStatus()
      // no point refreshing index if we're already building the other one
      .failIf(IndexStatus::isBuilding) { BuildAlreadyInProgressException(it) }
      // don't want to run two refreshes at the same time
      .failIf({ indexQueueStatus.active }) {
        ActiveMessagesExistException(indexQueueStatus, "build index")
      }
      .run {
        log.info("Sending {} refresh request", type)
        indexQueueService.sendIndexMessage(type, domainEvents)
      }
  }

  fun refreshIndex(domainEvents: Boolean = true): Int = indexStatusService.getIndexStatus()
    // no point refreshing index if we're already building
    .failIf(IndexStatus::isBuilding) { BuildAlreadyInProgressException(it) }
    .run { doRefreshIndex(domainEvents) }

  fun refreshActiveIndex(domainEvents: Boolean): Int = indexStatusService.getIndexStatus()
    // no point refreshing index if we're already building
    .failIf(IndexStatus::isBuilding) { BuildAlreadyInProgressException(it) }
    .run { doRefreshActiveIndex(domainEvents) }

  private fun doRefreshIndex(domainEvents: Boolean): Int {
    val ranges = nomisPrisonerService.getAllPrisonersIdRanges(active = false, size = pageSize)
    log.info("Found {} pages each of size {}", ranges.size, pageSize)
    return ranges
      .map { RootOffenderIdPage(it.fromRootOffenderId, it.toRootOffenderId) }
      .onEach { indexQueueService.sendRootOffenderIdPageMessage(it, REFRESH_PRISONER_PAGE, domainEvents) }.size
  }
  private fun doRefreshActiveIndex(domainEvents: Boolean): Int {
    val ranges = nomisPrisonerService.getAllPrisonersIdRanges(active = true, size = pageSize)
    log.info("Found {} pages each of size {}", ranges.size, pageSize)
    return ranges
      .map { RootOffenderIdPage(it.fromRootOffenderId, it.toRootOffenderId) }
      .onEach { indexQueueService.sendRootOffenderIdPageMessage(it, REFRESH_ACTIVE_PRISONER_PAGE, domainEvents) }.size
  }

  fun refreshIndexWithRootOffenderIdPage(page: RootOffenderIdPage, domainEvents: Boolean): Unit = nomisPrisonerService.getPrisonNumbers(
    active = false,
    fromRootOffenderId = page.fromRootOffenderId,
    toRootOffenderId = page.toRootOffenderId,
  ).forEach { indexQueueService.sendRefreshPrisonerMessage(prisonerNumber = it, domainEvents) }

  fun refreshActiveIndexWithRootOffenderIdPage(page: RootOffenderIdPage, domainEvents: Boolean): Unit = nomisPrisonerService.getPrisonNumbers(
    active = true,
    fromRootOffenderId = page.fromRootOffenderId,
    toRootOffenderId = page.toRootOffenderId,
  ).forEach { indexQueueService.sendRefreshPrisonerMessage(prisonerNumber = it, domainEvents) }

  fun refreshPrisoner(prisonerNumber: String, domainEvents: Boolean) {
    nomisService.getOffender(offenderNo = prisonerNumber)?.let { ob ->
      prisonerSynchroniserService.refreshAndReportDiffs(ob, domainEvents)
    }
  }

  private companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  private inline fun IndexStatus.failIf(
    check: (IndexStatus) -> Boolean,
    onFail: (IndexStatus) -> IndexException,
  ): IndexStatus = when (check(this)) {
    false -> this
    true -> throw onFail(this)
  }
}
