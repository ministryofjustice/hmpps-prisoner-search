@file:OptIn(ExperimentalCoroutinesApi::class)

package uk.gov.justice.digital.hmpps.prisonersearchindexer.services

import arrow.core.right
import com.microsoft.applicationinsights.TelemetryClient
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse
import uk.gov.justice.digital.hmpps.prisonersearchindexer.config.IndexBuildProperties
import uk.gov.justice.digital.hmpps.prisonersearchindexer.config.TelemetryEvents
import uk.gov.justice.digital.hmpps.prisonersearchindexer.model.IndexState.ABSENT
import uk.gov.justice.digital.hmpps.prisonersearchindexer.model.IndexState.BUILDING
import uk.gov.justice.digital.hmpps.prisonersearchindexer.model.IndexState.CANCELLED
import uk.gov.justice.digital.hmpps.prisonersearchindexer.model.IndexState.COMPLETED
import uk.gov.justice.digital.hmpps.prisonersearchindexer.model.IndexStatus
import uk.gov.justice.digital.hmpps.prisonersearchindexer.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearchindexer.model.SyncIndex.BLUE
import uk.gov.justice.digital.hmpps.prisonersearchindexer.model.SyncIndex.GREEN
import uk.gov.justice.digital.hmpps.prisonersearchindexer.model.SyncIndex.NONE
import uk.gov.justice.digital.hmpps.prisonersearchindexer.repository.PrisonerRepository
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import java.util.concurrent.CompletableFuture

class MaintainIndexServiceTest {

  private val indexStatusService = mock<IndexStatusService>()
  private val prisonerSynchroniserService = mock<PrisonerSynchroniserService>()
  private val indexQueueService = mock<IndexQueueService>()
  private val prisonerRepository = mock<PrisonerRepository>()
  private val hmppsQueueService = mock<HmppsQueueService>()
  private val telemetryClient = mock<TelemetryClient>()
  private val indexBuildProperties = mock<IndexBuildProperties>()
  private val maintainIndexService = MaintainIndexService(indexStatusService, prisonerSynchroniserService, indexQueueService, prisonerRepository, hmppsQueueService, telemetryClient, indexBuildProperties)

  private val indexSqsClient = mock<SqsAsyncClient>()
  private val indexSqsDlqClient = mock<SqsAsyncClient>()

  init {
    whenever(hmppsQueueService.findByQueueId("index")).thenReturn(HmppsQueue("index", indexSqsClient, "index-queue", indexSqsDlqClient, "index-dlq"))
    whenever(indexSqsClient.getQueueUrl(any<GetQueueUrlRequest>())).thenReturn(CompletableFuture.completedFuture(GetQueueUrlResponse.builder().queueUrl("arn:eu-west-1:index-queue").build()))
    whenever(indexSqsDlqClient.getQueueUrl(any<GetQueueUrlRequest>())).thenReturn(CompletableFuture.completedFuture(GetQueueUrlResponse.builder().queueUrl("arn:eu-west-1:index-dlq").build()))
  }

  @Nested
  inner class BuildIndex {
    @BeforeEach
    internal fun setUp() {
      whenever(indexStatusService.initialiseIndexWhenRequired()).thenReturn(indexStatusService)
      whenever(indexQueueService.getIndexQueueStatus()).thenReturn(IndexQueueStatus(0, 0, 0))
    }

    @Test
    fun `Index already building returns error`() {
      val expectedIndexStatus = IndexStatus(currentIndex = GREEN, otherIndexState = BUILDING)
      whenever(indexStatusService.getIndexStatus()).thenReturn(expectedIndexStatus)

      val result = maintainIndexService.prepareIndexForRebuild()

      verify(indexStatusService).getIndexStatus()
      result shouldBeLeft BuildAlreadyInProgressError(expectedIndexStatus)
    }

    @Test
    fun `Index has active messages returns an error`() {
      whenever(indexStatusService.getIndexStatus()).thenReturn(IndexStatus(currentIndex = GREEN, otherIndexState = COMPLETED))
      val expectedIndexQueueStatus = IndexQueueStatus(1, 0, 0)
      whenever(indexQueueService.getIndexQueueStatus()).thenReturn(expectedIndexQueueStatus)

      val result = maintainIndexService.prepareIndexForRebuild()

      verify(indexStatusService).getIndexStatus()
      result shouldBeLeft ActiveMessagesExistError(BLUE, expectedIndexQueueStatus, "build index")
    }

    @Test
    fun `A request is made to mark the index build is in progress`() {
      whenever(indexStatusService.getIndexStatus())
        .thenReturn(IndexStatus(currentIndex = GREEN, otherIndexState = ABSENT))

      maintainIndexService.prepareIndexForRebuild()

      verify(indexStatusService).markBuildInProgress()
    }

    @Test
    internal fun `will delete the index if it exists`() {
      whenever(indexStatusService.getIndexStatus())
        .thenReturn(IndexStatus(currentIndex = GREEN, otherIndexState = ABSENT))

      whenever(prisonerRepository.doesIndexExist(any())).thenReturn(true).thenReturn(false)

      maintainIndexService.prepareIndexForRebuild()

      verify(prisonerRepository).deleteIndex(BLUE)
    }

    @Test
    internal fun `won't bother deleting index if it does not exist`() {
      whenever(indexStatusService.getIndexStatus())
        .thenReturn(IndexStatus(currentIndex = GREEN, otherIndexState = ABSENT))
      whenever(prisonerRepository.doesIndexExist(BLUE)).thenReturn(false)

      maintainIndexService.prepareIndexForRebuild()

      verify(prisonerRepository, never()).deleteIndex(any())
    }

    @Test
    fun `waits for index to be deleted before recreating`() {
      whenever(indexStatusService.getIndexStatus())
        .thenReturn(IndexStatus(currentIndex = GREEN, otherIndexState = ABSENT))
      whenever(prisonerRepository.doesIndexExist(BLUE))
        .thenReturn(true)
        .thenReturn(true)
        .thenReturn(true)
        .thenReturn(false)

      maintainIndexService.prepareIndexForRebuild()

      verify(prisonerRepository, times(4)).doesIndexExist(BLUE)
      verify(prisonerRepository).deleteIndex(BLUE)
      verify(prisonerRepository).createIndex(BLUE)
    }

    @Test
    fun `A request is made to reset the other index`() {
      whenever(indexStatusService.getIndexStatus())
        .thenReturn(IndexStatus(currentIndex = GREEN, otherIndexState = ABSENT))

      whenever(prisonerRepository.doesIndexExist(any())).thenReturn(true).thenReturn(false)

      maintainIndexService.prepareIndexForRebuild()

      verify(prisonerRepository).createIndex(BLUE)
    }

    @Test
    fun `A request is made to build other index`() {
      whenever(indexStatusService.getIndexStatus())
        .thenReturn(IndexStatus(currentIndex = GREEN, otherIndexState = ABSENT))

      maintainIndexService.prepareIndexForRebuild()

      verify(indexQueueService).sendPopulateIndexMessage(any())
    }

    @Test
    fun `A telemetry event is sent`() {
      whenever(indexStatusService.getIndexStatus())
        .thenReturn(IndexStatus(currentIndex = GREEN, otherIndexState = ABSENT))

      maintainIndexService.prepareIndexForRebuild()

      verify(telemetryClient).trackEvent(TelemetryEvents.BUILDING_INDEX.name, mapOf("index" to "BLUE"), null)
    }

    @Test
    fun `The updated index is returned`() {
      val expectedIndexStatus = IndexStatus(currentIndex = GREEN, otherIndexState = BUILDING)
      whenever(indexStatusService.getIndexStatus())
        .thenReturn(IndexStatus(currentIndex = GREEN, otherIndexState = ABSENT))
        .thenReturn(expectedIndexStatus)

      val result = maintainIndexService.prepareIndexForRebuild()

      verify(indexStatusService, times(2)).getIndexStatus()
      result shouldBeRight expectedIndexStatus
    }
  }

  @Nested
  inner class MarkIndexingComplete {
    @BeforeEach
    internal fun setUp() {
      whenever(indexStatusService.markBuildCompleteAndSwitchIndex()).thenReturn(IndexStatus(currentIndex = GREEN, otherIndexState = COMPLETED))
      whenever(indexQueueService.getIndexQueueStatus()).thenReturn(IndexQueueStatus(0, 0, 0))
    }

    @Test
    fun `Index not building returns error`() {
      val expectedIndexStatus = IndexStatus(currentIndex = GREEN, otherIndexState = COMPLETED)
      whenever(indexStatusService.getIndexStatus()).thenReturn(expectedIndexStatus)

      val result = maintainIndexService.markIndexingComplete(ignoreThreshold = true)

      verify(indexStatusService).getIndexStatus()
      result shouldBeLeft BuildNotInProgressError(expectedIndexStatus)
    }

    @Test
    fun `Index with active messages returns error`() {
      whenever(indexStatusService.getIndexStatus()).thenReturn(IndexStatus(currentIndex = GREEN, otherIndexState = BUILDING))
      val expectedIndexQueueStatus = IndexQueueStatus(1, 0, 0)
      whenever(indexQueueService.getIndexQueueStatus()).thenReturn(expectedIndexQueueStatus)

      val result = maintainIndexService.markIndexingComplete(ignoreThreshold = true)

      verify(indexStatusService).getIndexStatus()
      result shouldBeLeft ActiveMessagesExistError(BLUE, expectedIndexQueueStatus, "mark complete")
    }

    @Test
    fun `Index not reached threshold returns error`() {
      whenever(indexStatusService.getIndexStatus()).thenReturn(IndexStatus(currentIndex = GREEN, otherIndexState = BUILDING))
      val expectedIndexQueueStatus = IndexQueueStatus(0, 0, 0)
      whenever(indexQueueService.getIndexQueueStatus()).thenReturn(expectedIndexQueueStatus)
      whenever(indexBuildProperties.completeThreshold).thenReturn(1000000)

      val result = maintainIndexService.markIndexingComplete(ignoreThreshold = false)

      verify(indexStatusService).getIndexStatus()
      verify(indexQueueService).getIndexQueueStatus()
      result shouldBeLeft ThresholdNotReachedError(BLUE, 1000000)
    }

    @Test
    fun `A request is made to mark the index state as complete`() {
      whenever(indexStatusService.getIndexStatus())
        .thenReturn(IndexStatus(currentIndex = GREEN, otherIndexState = BUILDING))
        .thenReturn(IndexStatus(currentIndex = BLUE, currentIndexState = COMPLETED))
      whenever(indexStatusService.markBuildCompleteAndSwitchIndex()).thenReturn(IndexStatus(currentIndex = BLUE, currentIndexState = COMPLETED))

      maintainIndexService.markIndexingComplete(ignoreThreshold = true)

      verify(indexStatusService).markBuildCompleteAndSwitchIndex()
    }

    @Test
    fun `Index not reached threshold but ignoring threshold - completes ok`() {
      whenever(indexStatusService.getIndexStatus()).thenReturn(IndexStatus(currentIndex = GREEN, otherIndexState = BUILDING))
      val expectedIndexQueueStatus = IndexQueueStatus(0, 0, 0)
      whenever(indexQueueService.getIndexQueueStatus()).thenReturn(expectedIndexQueueStatus)
      whenever(indexBuildProperties.completeThreshold).thenReturn(1000000)

      val result = maintainIndexService.markIndexingComplete(ignoreThreshold = false)

      verify(indexStatusService).getIndexStatus()
      verify(indexQueueService).getIndexQueueStatus()
      result shouldBeLeft ThresholdNotReachedError(BLUE, 1000000)
    }

    @Test
    fun `A telemetry event is sent`() {
      whenever(indexStatusService.getIndexStatus())
        .thenReturn(IndexStatus(currentIndex = GREEN, otherIndexState = BUILDING))
        .thenReturn(IndexStatus(currentIndex = BLUE, currentIndexState = COMPLETED))
      whenever(indexStatusService.markBuildCompleteAndSwitchIndex()).thenReturn(IndexStatus(currentIndex = BLUE, currentIndexState = COMPLETED))

      maintainIndexService.markIndexingComplete(ignoreThreshold = true)

      verify(telemetryClient).trackEvent(TelemetryEvents.COMPLETED_BUILDING_INDEX.name, mapOf("index" to "BLUE"), null)
    }

    @Test
    fun `Once current index marked as complete, the 'other' index is current`() {
      whenever(indexStatusService.getIndexStatus())
        .thenReturn(IndexStatus(currentIndex = GREEN, otherIndexState = BUILDING))
        .thenReturn(IndexStatus(currentIndex = BLUE, currentIndexState = COMPLETED))
      whenever(indexStatusService.markBuildCompleteAndSwitchIndex()).thenReturn(IndexStatus(currentIndex = BLUE, currentIndexState = COMPLETED))

      val result = maintainIndexService.markIndexingComplete(ignoreThreshold = true)

      verify(indexStatusService, times(2)).getIndexStatus()
      result shouldBeRight IndexStatus(currentIndex = BLUE, currentIndexState = COMPLETED)
    }
  }

  @Nested
  inner class SwitchIndex {

    @Test
    fun `A request is made to switch the indexes`() {
      whenever(indexStatusService.getIndexStatus())
        .thenReturn(IndexStatus(currentIndex = GREEN, otherIndexState = COMPLETED))
        .thenReturn(IndexStatus(currentIndex = BLUE, currentIndexState = COMPLETED))
      whenever(indexStatusService.switchIndex()).thenReturn(IndexStatus(currentIndex = BLUE, currentIndexState = COMPLETED))

      val result = maintainIndexService.switchIndex(false)

      verify(indexStatusService).switchIndex()
      result shouldBeRight IndexStatus(currentIndex = BLUE, currentIndexState = COMPLETED)
    }

    @Test
    fun `A request is made to switch alias`() {
      whenever(indexStatusService.getIndexStatus())
        .thenReturn(IndexStatus(currentIndex = GREEN, otherIndexState = COMPLETED))
        .thenReturn(IndexStatus(currentIndex = BLUE, currentIndexState = COMPLETED))
      whenever(indexStatusService.switchIndex()).thenReturn(IndexStatus(currentIndex = BLUE, currentIndexState = COMPLETED))

      maintainIndexService.switchIndex(false)

      verify(prisonerRepository).switchAliasIndex(BLUE)
    }

    @Test
    fun `A telemetry event is sent`() {
      whenever(indexStatusService.getIndexStatus())
        .thenReturn(IndexStatus(currentIndex = GREEN, otherIndexState = COMPLETED))
        .thenReturn(IndexStatus(currentIndex = BLUE, currentIndexState = COMPLETED))
      whenever(indexStatusService.switchIndex()).thenReturn(IndexStatus(currentIndex = BLUE, currentIndexState = COMPLETED))

      maintainIndexService.switchIndex(false)

      verify(telemetryClient).trackEvent(TelemetryEvents.SWITCH_INDEX.name, mapOf("index" to "BLUE"), null)
    }

    @Test
    fun `Index building returns error`() {
      val expectedIndexStatus = IndexStatus(currentIndex = GREEN, otherIndexState = BUILDING)
      whenever(indexStatusService.getIndexStatus()).thenReturn(expectedIndexStatus)

      val result = maintainIndexService.switchIndex(false)

      verify(indexStatusService).getIndexStatus()
      result shouldBeLeft BuildInProgressError(expectedIndexStatus)
    }

    @Test
    fun `Index Cancelled returns error`() {
      val expectedIndexStatus = IndexStatus(currentIndex = GREEN, otherIndexState = CANCELLED)
      whenever(indexStatusService.getIndexStatus()).thenReturn(expectedIndexStatus)

      val result = maintainIndexService.switchIndex(false)

      verify(indexStatusService).getIndexStatus()
      result shouldBeLeft BuildCancelledError(expectedIndexStatus)
    }

    @Test
    fun `Index Absent returns error`() {
      val expectedIndexStatus = IndexStatus(currentIndex = GREEN, otherIndexState = ABSENT)
      whenever(indexStatusService.getIndexStatus()).thenReturn(expectedIndexStatus)

      val result = maintainIndexService.switchIndex(false)

      verify(indexStatusService).getIndexStatus()
      result shouldBeLeft BuildAbsentError(expectedIndexStatus)
    }
  }

  @Nested
  inner class CancelIndexing {

    @Test
    fun `Index not building returns error`() {
      val expectedIndexStatus = IndexStatus(currentIndex = GREEN, otherIndexState = COMPLETED)
      whenever(indexStatusService.getIndexStatus()).thenReturn(expectedIndexStatus)

      val result = maintainIndexService.cancelIndexing()

      verify(indexStatusService).getIndexStatus()
      result shouldBeLeft BuildNotInProgressError(expectedIndexStatus)
    }

    @Test
    fun `A request is made to mark the index state as cancelled`() {
      val expectedIndexStatus = IndexStatus(currentIndex = GREEN, otherIndexState = BUILDING)
      whenever(indexStatusService.getIndexStatus()).thenReturn(expectedIndexStatus)

      maintainIndexService.cancelIndexing()

      verify(indexStatusService).markBuildCancelled()
    }

    @Test
    fun `all messages are cleared`() = runTest {
      val expectedIndexStatus = IndexStatus(currentIndex = GREEN, otherIndexState = BUILDING)
      whenever(indexStatusService.getIndexStatus()).thenReturn(expectedIndexStatus)

      maintainIndexService.cancelIndexing()

      verify(hmppsQueueService).purgeQueue(
        check {
          assertThat(it.queueName).isEqualTo("index-queue")
        },
      )
    }

    @Test
    fun `A telemetry event is sent`() {
      val expectedIndexStatus = IndexStatus(currentIndex = GREEN, otherIndexState = BUILDING)
      whenever(indexStatusService.getIndexStatus()).thenReturn(expectedIndexStatus)

      maintainIndexService.cancelIndexing()

      verify(telemetryClient).trackEvent(TelemetryEvents.CANCELLED_BUILDING_INDEX.name, mapOf("index" to "BLUE"), null)
    }

    @Test
    fun `Once current index marked as cancelled, the 'other' index is current`() {
      val expectedIndexStatus = IndexStatus(currentIndex = GREEN, otherIndexState = CANCELLED)
      whenever(indexStatusService.getIndexStatus())
        .thenReturn(IndexStatus(currentIndex = GREEN, otherIndexState = BUILDING))
        .thenReturn(expectedIndexStatus)

      val result = maintainIndexService.cancelIndexing()

      verify(indexStatusService, times(2)).getIndexStatus()
      result shouldBeRight expectedIndexStatus
    }
  }

  @Nested
  inner class IndexOffender {
    @BeforeEach
    internal fun setUp() {
      whenever(prisonerSynchroniserService.reindex(any(), any())).thenReturn(Prisoner().right())
    }

    @Test
    internal fun `will delegate to synchronisation service`() {
      val indexStatus = IndexStatus(currentIndex = GREEN, currentIndexState = COMPLETED, otherIndexState = ABSENT)
      whenever(indexStatusService.getIndexStatus()).thenReturn(indexStatus)

      maintainIndexService.updatePrisoner("ABC123D")

      verify(prisonerSynchroniserService).reindex("ABC123D", GREEN)
    }
  }

  @Nested
  inner class UpdatePrisoner {
    @Test
    fun `No active indexes, update is not requested`() {
      whenever(indexStatusService.getIndexStatus()).thenReturn(IndexStatus.newIndex())

      maintainIndexService.updatePrisoner("SOME_CRN")

      verifyNoInteractions(prisonerSynchroniserService)
    }

    @Test
    fun `No active indexes, error is returned`() {
      val indexStatus = IndexStatus.newIndex()
      whenever(indexStatusService.getIndexStatus()).thenReturn(indexStatus)

      val result = maintainIndexService.updatePrisoner("SOME_CRN")

      result shouldBeLeft NoActiveIndexesError(indexStatus)
    }

    @Test
    fun `Current index active, offender is updated`() {
      val indexStatus = IndexStatus(currentIndex = GREEN, currentIndexState = COMPLETED)
      whenever(indexStatusService.getIndexStatus()).thenReturn(indexStatus)
      whenever(prisonerSynchroniserService.reindex(any(), any())).thenReturn(Prisoner().right())

      maintainIndexService.updatePrisoner("SOME_CRN")

      verify(prisonerSynchroniserService).reindex("SOME_CRN", indexStatus.currentIndex)
    }

    @Test
    fun `Other index active, offender is updated`() {
      val indexStatus = IndexStatus(currentIndex = NONE, otherIndexState = BUILDING, currentIndexState = ABSENT)
      whenever(indexStatusService.getIndexStatus()).thenReturn(indexStatus)
      whenever(prisonerSynchroniserService.reindex(any(), any())).thenReturn(Prisoner().right())

      maintainIndexService.updatePrisoner("SOME_CRN")

      verify(prisonerSynchroniserService).reindex("SOME_CRN", indexStatus.otherIndex)
    }

    @Test
    fun `Both indexes active, offender is updated on both indexes`() {
      val indexStatus = IndexStatus(currentIndex = GREEN, otherIndexState = BUILDING, currentIndexState = COMPLETED)
      whenever(indexStatusService.getIndexStatus()).thenReturn(indexStatus)
      whenever(prisonerSynchroniserService.reindex(any(), any())).thenReturn(Prisoner().right())

      maintainIndexService.updatePrisoner("SOME_CRN")

      verify(prisonerSynchroniserService).reindex(eq("SOME_CRN"), eq(GREEN), eq(BLUE))
    }
  }
}
