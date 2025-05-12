package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IndexStatus
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
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
) {
  private val indexQueue by lazy { hmppsQueueService.findByQueueId("index") as HmppsQueue }

  fun prepareIndexForRebuild(): IndexStatus {
    val indexQueueStatus = indexQueueService.getIndexQueueStatus()
    return indexStatusService.getIndexStatus()
      .also { logIndexStatuses(it) }
      .failIf(IndexStatus::isBuilding) { BuildAlreadyInProgressException(it) }
      .failIf({ indexQueueStatus.active }) { ActiveMessagesExistException(indexQueueStatus, "build index") }
      .run { doPrepareIndexForRebuild() }
  }

  private fun doPrepareIndexForRebuild(): IndexStatus {
    indexStatusService.markBuildInProgress()
    indexQueueService.sendPopulateIndexMessage()
    return indexStatusService.getIndexStatus()
      .also { indexStatus ->
        logIndexStatuses(indexStatus)
        telemetryClient.trackEvent(
          TelemetryEvents.BUILDING_INDEX,
          mapOf("index-state" to indexStatus.currentIndexState.name),
        )
      }
  }

  fun logIndexStatuses(indexStatus: IndexStatus) {
    log.info(
      "Current index status is {}.  Index count={}.  Queue counts: Queue={} and DLQ={}",
      indexStatus,
      prisonerRepository.count(),
      indexQueueService.getNumberOfMessagesCurrentlyOnIndexQueue(),
      indexQueueService.getNumberOfMessagesCurrentlyOnIndexDLQ(),
    )
  }

  fun markIndexingComplete(): IndexStatus {
    val indexQueueStatus = indexQueueService.getIndexQueueStatus()
    val indexStatus = indexStatusService.getIndexStatus()
    return indexStatus
      .failIf(IndexStatus::isNotBuilding) { BuildNotInProgressException(it) }
      .failIf({ indexQueueStatus.active }) {
        ActiveMessagesExistException(
          indexQueueStatus,
          "mark complete",
        )
      }
      .also { logIndexStatuses(indexStatus) }
      .run { doMarkIndexingComplete() }
  }

  private fun doMarkIndexingComplete(): IndexStatus = indexStatusService.markBuildComplete()
    .let { newStatus ->
      return indexStatusService.getIndexStatus()
        .also { latestStatus -> logIndexStatuses(latestStatus) }
        .also {
          telemetryClient.trackEvent(
            TelemetryEvents.COMPLETED_BUILDING_INDEX,
            mapOf("index-state" to newStatus.toString()),
          )
        }
    }

  fun cancelIndexing(): IndexStatus = indexStatusService.getIndexStatus()
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
          mapOf("index-state" to it.currentIndexState.name),
        )
      }
  }

  fun indexPrisoner(prisonerNumber: String): Prisoner {
    val offenderBooking = nomisService.getOffender(prisonerNumber)
    return offenderBooking
      ?.also {
        prisonerSynchroniserService.reindexIncentive(prisonerNumber, "MAINTAIN")
        prisonerSynchroniserService.reindexRestrictedPatient(prisonerNumber, offenderBooking, "MAINTAIN")
        prisonerSynchroniserService.reindexAlerts(prisonerNumber, "MAINTAIN")
        prisonerSynchroniserService.reindexComplexityOfNeedWithGet(offenderBooking, "MAINTAIN")
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

  private inline fun IndexStatus.failIf(
    check: (IndexStatus) -> Boolean,
    onFail: (IndexStatus) -> IndexException,
  ): IndexStatus = when (check(this)) {
    false -> this
    true -> throw onFail(this)
  }

  private companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
