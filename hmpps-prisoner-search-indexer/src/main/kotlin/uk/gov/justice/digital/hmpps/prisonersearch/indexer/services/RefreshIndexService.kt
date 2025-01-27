package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IndexStatus
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.SyncIndex
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.IndexBuildProperties
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.repository.PrisonerDifferencesLabel

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
      // no point if there is no data in the active index
      .failIf(IndexStatus::activeIndexesEmpty) { BuildAbsentException(it) }
      // don't want to run two refreshes at the same time
      .failIf({ indexQueueStatus.active }) {
        ActiveMessagesExistException(it.currentIndex, indexQueueStatus, "build index")
      }
      .run {
        log.info("Sending index refresh request")
        indexQueueService.sendRefreshIndexMessage(currentIndex)
      }
  }

  fun refreshIndex(): Int = indexStatusService.getIndexStatus()
    // no point refreshing index if we're already building the other one
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
    indexStatusService.getIndexStatus()
      // no point refreshing index if we're already building the other one
      .failIf(IndexStatus::isBuilding) { BuildAlreadyInProgressException(it) }
      .run {
        nomisService.getOffender(prisonerNumber)?.let { ob ->
          val (incentiveLevelData, restrictedPatientData) = prisonerSynchroniserService.getDomainData(ob)
          prisonerSynchroniserService.compareAndMaybeIndex(ob, incentiveLevelData, restrictedPatientData, activeIndexes(), PrisonerDifferencesLabel.GREEN_BLUE)
          prisonerSynchroniserService.compareAndMaybeIndex(ob, incentiveLevelData, restrictedPatientData, listOf(SyncIndex.RED), PrisonerDifferencesLabel.RED)
        }
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
