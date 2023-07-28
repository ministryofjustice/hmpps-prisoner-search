package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.runBlocking
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IndexStatus
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.SyncIndex
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.IndexBuildProperties
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.TelemetryEvents
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.TelemetryEvents.PRISONER_NOT_FOUND
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.trackPrisonerEvent
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.repository.PrisonerRepository
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.PurgeQueueRequest

@Service
class MaintainIndexService(
  private val indexStatusService: IndexStatusService,
  private val prisonerSynchroniserService: PrisonerSynchroniserService,
  private val indexQueueService: IndexQueueService,
  private val prisonerRepository: PrisonerRepository,
  private val hmppsQueueService: HmppsQueueService,
  private val nomisService: NomisService,
  private val telemetryClient: TelemetryClient,
  private val indexBuildProperties: IndexBuildProperties,
) {
  private val indexQueue by lazy { hmppsQueueService.findByQueueId("index") as HmppsQueue }

  fun prepareIndexForRebuild(): IndexStatus {
    val indexQueueStatus = indexQueueService.getIndexQueueStatus()
    return indexStatusService.initialiseIndexWhenRequired().getIndexStatus()
      .also { logIndexStatuses(it) }
      .failIf(IndexStatus::isBuilding) { BuildAlreadyInProgressException(it) }
      .failIf({ indexQueueStatus.active }) { ActiveMessagesExistException(it.otherIndex, indexQueueStatus, "build index") }
      .run { doPrepareIndexForRebuild(this) }
  }

  private fun doPrepareIndexForRebuild(indexStatus: IndexStatus): IndexStatus {
    indexStatusService.markBuildInProgress()
    checkExistsAndReset(indexStatus.otherIndex)
    indexQueueService.sendPopulateIndexMessage(indexStatus.otherIndex)
    return indexStatusService.getIndexStatus()
      .also { logIndexStatuses(it) }
      .also {
        telemetryClient.trackEvent(
          TelemetryEvents.BUILDING_INDEX,
          mapOf("index" to indexStatus.otherIndex.name),
        )
      }
  }

  private fun checkExistsAndReset(index: SyncIndex) {
    if (prisonerRepository.doesIndexExist(index)) {
      prisonerRepository.deleteIndex(index)
    }
    await untilCallTo { prisonerRepository.doesIndexExist(index) } matches { it == false }
    prisonerRepository.createIndex(index)
  }

  fun logIndexStatuses(indexStatus: IndexStatus) {
    log.info(
      "Current index status is {}.  Index counts {}={} and {}={}.  Queue counts: Queue={} and DLQ={}",
      indexStatus,
      indexStatus.currentIndex,
      prisonerRepository.count(indexStatus.currentIndex),
      indexStatus.otherIndex,
      prisonerRepository.count(indexStatus.otherIndex),
      indexQueueService.getNumberOfMessagesCurrentlyOnIndexQueue(),
      indexQueueService.getNumberOfMessagesCurrentlyOnIndexDLQ(),
    )
  }

  fun markIndexingComplete(ignoreThreshold: Boolean): IndexStatus {
    val indexQueueStatus = indexQueueService.getIndexQueueStatus()
    val indexStatus = indexStatusService.getIndexStatus()
    return indexStatus
      .failIf(IndexStatus::isNotBuilding) { BuildNotInProgressException(it) }
      .failIf({ indexQueueStatus.active }) {
        ActiveMessagesExistException(
          it.otherIndex,
          indexQueueStatus,
          "mark complete",
        )
      }
      .failIf({ ignoreThreshold.not() && indexSizeNotReachedThreshold(it) }) {
        ThresholdNotReachedException(
          it.otherIndex,
          indexBuildProperties.completeThreshold,
        )
      }
      .also { logIndexStatuses(indexStatus) }
      .run { doMarkIndexingComplete() }
  }

  private fun indexSizeNotReachedThreshold(indexStatus: IndexStatus): Boolean =
    prisonerRepository.count(indexStatus.currentIndex.otherIndex()) < indexBuildProperties.completeThreshold

  private fun doMarkIndexingComplete(): IndexStatus =
    indexStatusService.markBuildCompleteAndSwitchIndex()
      .let { newStatus ->
        prisonerRepository.switchAliasIndex(newStatus.currentIndex)
        return indexStatusService.getIndexStatus()
          .also { latestStatus -> logIndexStatuses(latestStatus) }
          .also {
            telemetryClient.trackEvent(
              TelemetryEvents.COMPLETED_BUILDING_INDEX,
              mapOf("index" to it.currentIndex.name),
            )
          }
      }

  fun switchIndex(force: Boolean): IndexStatus {
    val indexStatus = indexStatusService.getIndexStatus()
    return when {
      force ->
        indexStatus
          .failIf(IndexStatus::isAbsent) { BuildAbsentException(it) }

      else ->
        indexStatus
          .failIf(IndexStatus::isAbsent) { BuildAbsentException(it) }
          .failIf(IndexStatus::isBuilding) { BuildInProgressException(it) }
          .failIf(IndexStatus::isCancelled) { BuildCancelledException(it) }
    }
      .also { logIndexStatuses(indexStatus) }
      .run { doSwitchIndex() }
  }

  private fun doSwitchIndex(): IndexStatus =
    indexStatusService.switchIndex()
      .let { newStatus ->
        prisonerRepository.switchAliasIndex(newStatus.currentIndex)
        return indexStatusService.getIndexStatus()
          .also { latestStatus -> logIndexStatuses(latestStatus) }
          .also {
            telemetryClient.trackEvent(
              TelemetryEvents.SWITCH_INDEX,
              mapOf("index" to it.currentIndex.name),
            )
          }
      }

  fun cancelIndexing(): IndexStatus =
    indexStatusService.getIndexStatus()
      .also { logIndexStatuses(it) }
      .failIf(IndexStatus::isNotBuilding) { BuildNotInProgressException(it) }
      .run { doCancelIndexing() }

  private fun doCancelIndexing(): IndexStatus {
    indexStatusService.markBuildCancelled()
    runBlocking {
      hmppsQueueService.purgeQueue(PurgeQueueRequest(indexQueue.queueName, indexQueue.sqsClient, indexQueue.queueUrl))
    }
    return indexStatusService.getIndexStatus()
      .also { logIndexStatuses(it) }
      .also {
        telemetryClient.trackEvent(
          TelemetryEvents.CANCELLED_BUILDING_INDEX,
          mapOf("index" to it.otherIndex.name),
        )
      }
  }

  fun indexPrisoner(prisonerNumber: String): Prisoner =
    indexStatusService.getIndexStatus()
      .failIf(IndexStatus::activeIndexesEmpty) {
        log.info("Ignoring update of prisoner {} as no indexes were active", prisonerNumber)
        NoActiveIndexesException(it)
      }
      .run { sync(prisonerNumber, this.activeIndexes()) }

  private fun sync(prisonerNumber: String, activeIndices: List<SyncIndex>) =
    nomisService.getOffender(prisonerNumber)?.let { ob ->
      prisonerSynchroniserService.reindex(ob, activeIndices)
    }
      ?: prisonerRepository.get(prisonerNumber, activeIndices)
        ?.run {
          // Prisoner not in NOMIS, but found in index so remove
          prisonerSynchroniserService.delete(prisonerNumber)
          this
        }
      ?: run {
        // not found in either NOMIS or index, so log and throw
        telemetryClient.trackPrisonerEvent(PRISONER_NOT_FOUND, prisonerNumber)
        throw PrisonerNotFoundException(prisonerNumber)
      }

  private inline fun IndexStatus.failIf(
    check: (IndexStatus) -> Boolean,
    onFail: (IndexStatus) -> IndexException,
  ): IndexStatus =
    when (check(this)) {
      false -> this
      true -> throw onFail(this)
    }

  private companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
