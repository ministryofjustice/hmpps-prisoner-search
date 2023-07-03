package uk.gov.justice.digital.hmpps.prisonersearchindexer.services

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.runBlocking
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearchindexer.config.IndexBuildProperties
import uk.gov.justice.digital.hmpps.prisonersearchindexer.config.TelemetryEvents
import uk.gov.justice.digital.hmpps.prisonersearchindexer.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonersearchindexer.model.IndexStatus
import uk.gov.justice.digital.hmpps.prisonersearchindexer.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearchindexer.model.SyncIndex
import uk.gov.justice.digital.hmpps.prisonersearchindexer.repository.PrisonerRepository
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.PurgeQueueRequest
import kotlin.reflect.KClass

@Service
class MaintainIndexService(
  private val indexStatusService: IndexStatusService,
  private val prisonerSynchroniserService: PrisonerSynchroniserService,
  private val indexQueueService: IndexQueueService,
  private val prisonerRepository: PrisonerRepository,
  private val hmppsQueueService: HmppsQueueService,
  private val telemetryClient: TelemetryClient,
  private val indexBuildProperties: IndexBuildProperties,
) {
  private val indexQueue by lazy { hmppsQueueService.findByQueueId("index") as HmppsQueue }

  fun prepareIndexForRebuild(): Either<Error, IndexStatus> {
    val indexQueueStatus = indexQueueService.getIndexQueueStatus()
    return indexStatusService.initialiseIndexWhenRequired().getIndexStatus()
      .also { logIndexStatuses(it) }
      .failIf(IndexStatus::isBuilding) { BuildAlreadyInProgressError(it) }
      .failIf({ indexQueueStatus.active }) { ActiveMessagesExistError(it.otherIndex, indexQueueStatus, "build index") }
      .map { doPrepareIndexForRebuild(it) }
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

  fun markIndexingComplete(ignoreThreshold: Boolean): Either<Error, IndexStatus> {
    val indexQueueStatus = indexQueueService.getIndexQueueStatus()
    val indexStatus = indexStatusService.getIndexStatus()
    return indexStatus
      .failIf(IndexStatus::isNotBuilding) { BuildNotInProgressError(it) }
      .failIf({ indexQueueStatus.active }) {
        ActiveMessagesExistError(
          it.otherIndex,
          indexQueueStatus,
          "mark complete",
        )
      }
      .failIf({ ignoreThreshold.not() && indexSizeNotReachedThreshold(it) }) {
        ThresholdNotReachedError(
          it.otherIndex,
          indexBuildProperties.completeThreshold,
        )
      }
      .also { logIndexStatuses(indexStatus) }
      .map { doMarkIndexingComplete() }
  }

  private fun indexSizeNotReachedThreshold(indexStatus: IndexStatus): Boolean =
    prisonerRepository.count(indexStatus.currentIndex) < indexBuildProperties.completeThreshold

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

  fun switchIndex(force: Boolean): Either<Error, IndexStatus> {
    val indexStatus = indexStatusService.getIndexStatus()
    return when {
      force -> {
        indexStatus
          .failIf(IndexStatus::isAbsent) { BuildAbsentError(it) }
          .also { logIndexStatuses(indexStatus) }
          .map { doSwitchIndex() }
      }

      else -> {
        indexStatus
          .failIf(IndexStatus::isAbsent) { BuildAbsentError(it) }
          .failIf(IndexStatus::isBuilding) { BuildInProgressError(it) }
          .failIf(IndexStatus::isCancelled) { BuildCancelledError(it) }
          .also { logIndexStatuses(indexStatus) }
          .map { doSwitchIndex() }
      }
    }
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

  fun cancelIndexing(): Either<Error, IndexStatus> =
    indexStatusService.getIndexStatus()
      .also { logIndexStatuses(it) }
      .failIf(IndexStatus::isNotBuilding) { BuildNotInProgressError(it) }
      .map { doCancelIndexing() }

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

  fun updatePrisoner(prisonerNumber: String): Either<Error, Prisoner> =
    indexStatusService.getIndexStatus()
      .failIf(IndexStatus::activeIndexesEmpty) {
        log.info("Ignoring update of prisoner {} as no indexes were active", prisonerNumber)
        NoActiveIndexesError(it)
      }
      .flatMap { doUpdatePrisoner(it, prisonerNumber) }

  private fun doUpdatePrisoner(indexStatus: IndexStatus, prisonerNumber: String) =
    with(indexStatus.activeIndexes()) {
      log.info("Updating prisoner {} on indexes {}", prisonerNumber, this)
      prisonerSynchroniserService.synchronisePrisoner(prisonerNumber, *this.toTypedArray())
    }

  private inline fun IndexStatus.failIf(
    check: (IndexStatus) -> Boolean,
    onFail: (IndexStatus) -> Error,
  ): Either<Error, IndexStatus> =
    when (check(this)) {
      false -> this.right()
      true -> onFail(this).left()
    }

  private inline fun Either<Error, IndexStatus>.failIf(
    crossinline check: (IndexStatus) -> Boolean,
    crossinline onFail: (IndexStatus) -> Error,
  ): Either<Error, IndexStatus> =
    when (this.isLeft()) {
      true -> this
      false -> this.flatMap {
        it.failIf(check, onFail)
      }
    }

  private companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}

enum class UpdatePrisonerError(val errorClass: KClass<out Error>) {
  NO_ACTIVE_INDEXES(NoActiveIndexesError::class),
  PRISONER_NOT_FOUND(PrisonerNotFoundError::class),
  ;

  companion object {
    fun fromErrorClass(error: Error): UpdatePrisonerError = values().first { it.errorClass == error::class }
  }
}

enum class PrepareRebuildError(val errorClass: KClass<out Error>) {
  BUILD_IN_PROGRESS(BuildAlreadyInProgressError::class),
  ACTIVE_MESSAGES_EXIST(ActiveMessagesExistError::class),
  ;

  companion object {
    fun fromErrorClass(error: Error): PrepareRebuildError = values().first { it.errorClass == error::class }
  }
}

enum class MarkCompleteError(val errorClass: KClass<out Error>) {
  BUILD_NOT_IN_PROGRESS(BuildNotInProgressError::class),
  ACTIVE_MESSAGES_EXIST(ActiveMessagesExistError::class),
  THRESHOLD_NOT_REACHED(ThresholdNotReachedError::class),
  ;

  companion object {
    fun fromErrorClass(error: Error): MarkCompleteError = values().first { it.errorClass == error::class }
  }
}

enum class SwitchIndexError(val errorClass: KClass<out Error>) {
  BUILD_ABSENT(BuildAbsentError::class),
  BUILD_IN_PROGRESS(BuildInProgressError::class),
  BUILD_CANCELLED(BuildCancelledError::class),
  ;

  companion object {
    fun fromErrorClass(error: Error): SwitchIndexError = values().first { it.errorClass == error::class }
  }
}

enum class CancelBuildError(val errorClass: KClass<out Error>) {
  BUILD_NOT_IN_PROGRESS(BuildNotInProgressError::class),
  ;

  companion object {
    fun fromErrorClass(error: Error): CancelBuildError = values().first { it.errorClass == error::class }
  }
}
