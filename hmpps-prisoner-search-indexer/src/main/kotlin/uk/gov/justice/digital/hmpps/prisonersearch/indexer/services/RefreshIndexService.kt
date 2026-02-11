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
  private val prisonerSynchroniserService: PrisonerSynchroniserService,
  indexBuildProperties: IndexBuildProperties,
) {
  private val pageSize = indexBuildProperties.pageSize

  fun startFullIndexRefresh() {
    startIndexRefresh(REFRESH_INDEX)
  }

  fun startActiveIndexRefresh() {
    startIndexRefresh(REFRESH_ACTIVE_INDEX)
  }

  private fun startIndexRefresh(type: IndexRequestType) {
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
        indexQueueService.sendIndexMessage(type)
      }
  }

  fun refreshIndex(): Int = indexStatusService.getIndexStatus()
    // no point refreshing index if we're already building
    .failIf(IndexStatus::isBuilding) { BuildAlreadyInProgressException(it) }
    .run { doRefreshIndex() }

  fun refreshActiveIndex(): Int = indexStatusService.getIndexStatus()
    // no point refreshing index if we're already building
    .failIf(IndexStatus::isBuilding) { BuildAlreadyInProgressException(it) }
    .run { doRefreshActiveIndex() }

  private fun doRefreshIndex(): Int {
    val totalNumberOfPrisoners = nomisService.getTotalNumberOfPrisoners()
    log.info("Splitting {} into pages each of size {}", totalNumberOfPrisoners, pageSize)
    return (1..totalNumberOfPrisoners step pageSize.toLong())
      .map { PrisonerPage((it / pageSize).toInt(), pageSize) }
      .onEach { indexQueueService.sendPrisonerPageMessage(it, REFRESH_PRISONER_PAGE) }.size
  }
  private fun doRefreshActiveIndex(): Int {
    val totalNumberOfActivePrisoners = nomisService.getTotalNumberOfActivePrisoners()
    log.info("Splitting {} into active pages each of size {}", totalNumberOfActivePrisoners, pageSize)
    return (1..totalNumberOfActivePrisoners step pageSize.toLong())
      .map { PrisonerPage((it / pageSize).toInt(), pageSize) }
      .onEach { indexQueueService.sendPrisonerPageMessage(it, REFRESH_ACTIVE_PRISONER_PAGE) }.size
  }

  fun refreshIndexWithPrisonerPage(prisonerPage: PrisonerPage): Unit = nomisService.getPrisonerNumbers(prisonerPage.page, prisonerPage.pageSize)
    .forEach { indexQueueService.sendRefreshPrisonerMessage(it) }

  fun refreshActiveIndexWithPrisonerPage(prisonerPage: PrisonerPage): Unit = nomisService.getActivePrisonerNumbers(prisonerPage.page, prisonerPage.pageSize)
    .forEach { indexQueueService.sendRefreshPrisonerMessage(it) }

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
