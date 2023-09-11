package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IndexStatus

@Service
class RefreshIndexService(
  private val indexStatusService: IndexStatusService,
  private val indexQueueService: IndexQueueService,
) {
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

  private companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  private inline fun IndexStatus.failIf(
    check: (IndexStatus) -> Boolean,
    onFail: (IndexStatus) -> IndexException,
  ): IndexStatus =
    when (check(this)) {
      false -> this
      true -> throw onFail(this)
    }
}
