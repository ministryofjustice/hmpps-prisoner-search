package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IndexStatus
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.IndexBuildProperties

@Service
class RefreshIndexService(
  private val indexStatusService: IndexStatusService,
  private val indexQueueService: IndexQueueService,
  private val nomisService: NomisService,
  private val prisonerSynchroniserService: PrisonerSynchroniserService,
  indexBuildProperties: IndexBuildProperties,
) {
  private val pageSize = indexBuildProperties.pageSize

  fun startIndexRefresh() {
    val indexQueueStatus = indexQueueService.getIndexQueueStatus()
    return indexStatusService.getIndexStatus()
      // no point refreshing index if we're already building the other one
      .failIf(IndexStatus::isBuilding) { BuildAlreadyInProgressException(it) }
      // don't want to run two refreshes at the same time
      .failIf({ indexQueueStatus.active }) {
        ActiveMessagesExistException(indexQueueStatus, "build index")
      }
      .run {
        log.info("Sending index refresh request")
        indexQueueService.sendRefreshIndexMessage()
      }
  }

  fun refreshIndex(): Int = indexStatusService.getIndexStatus()
    // no point refreshing index if we're already building
    .failIf(IndexStatus::isBuilding) { BuildAlreadyInProgressException(it) }
    .run { doRefreshIndex() }

  private fun doRefreshIndex(): Int {
    val totalNumberOfPrisoners = nomisService.getTotalNumberOfPrisoners()
    log.info("Splitting $totalNumberOfPrisoners in to pages each of size $pageSize")
    return (1..totalNumberOfPrisoners step pageSize.toLong()).toList()
      .map { PrisonerPage((it / pageSize).toInt(), pageSize) }
      .onEach { indexQueueService.sendRefreshPrisonerPageMessage(it) }.size
  }

  fun refreshIndexWithPrisonerPage(prisonerPage: PrisonerPage): Unit = nomisService.getPrisonerNumbers(prisonerPage.page, prisonerPage.pageSize)
    .forEach {
      indexQueueService.sendRefreshPrisonerMessage(it)
    }

  fun refreshPrisoner(prisonerNumber: String) {
    nomisService.getOffender(prisonerNumber)?.let { ob ->
      prisonerSynchroniserService.refresh(ob)
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
