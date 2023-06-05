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
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.opensearch.OpenSearchStatusException
import org.opensearch.client.RestHighLevelClient
import org.opensearch.client.core.CountResponse
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
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import java.util.concurrent.CompletableFuture

class IndexServiceTest {

  private val indexStatusService = mock<IndexStatusService>()
  private val prisonerSynchroniserService = mock<PrisonerSynchroniserService>()
  private val indexQueueService = mock<IndexQueueService>()
  private val hmppsQueueService = mock<HmppsQueueService>()
  private val elasticSearchClient = mock<RestHighLevelClient>()
  private val telemetryClient = mock<TelemetryClient>()
  private val indexBuildProperties = mock<IndexBuildProperties>()
  private val indexService = IndexService(indexStatusService, prisonerSynchroniserService, indexQueueService, hmppsQueueService, elasticSearchClient, telemetryClient, indexBuildProperties)

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

      val result = indexService.prepareIndexForRebuild()

      verify(indexStatusService).getIndexStatus()
      result shouldBeLeft BuildAlreadyInProgressError(expectedIndexStatus)
    }

    @Test
    fun `Index has active messages returns an error`() {
      whenever(indexStatusService.getIndexStatus()).thenReturn(IndexStatus(currentIndex = GREEN, otherIndexState = COMPLETED))
      val expectedIndexQueueStatus = IndexQueueStatus(1, 0, 0)
      whenever(indexQueueService.getIndexQueueStatus()).thenReturn(expectedIndexQueueStatus)

      val result = indexService.prepareIndexForRebuild()

      verify(indexStatusService).getIndexStatus()
      result shouldBeLeft ActiveMessagesExistError(BLUE, expectedIndexQueueStatus, "build index")
    }

    @Test
    fun `A request is made to mark the index build is in progress`() {
      whenever(indexStatusService.getIndexStatus())
        .thenReturn(IndexStatus(currentIndex = GREEN, otherIndexState = ABSENT))

      indexService.prepareIndexForRebuild()

      verify(indexStatusService).markBuildInProgress()
    }

    @Test
    fun `A request is made to reset the other index`() {
      whenever(indexStatusService.getIndexStatus())
        .thenReturn(IndexStatus(currentIndex = GREEN, otherIndexState = ABSENT))

      indexService.prepareIndexForRebuild()

      verify(prisonerSynchroniserService).checkExistsAndReset(BLUE)
    }

    @Test
    fun `A request is made to build other index`() {
      whenever(indexStatusService.getIndexStatus())
        .thenReturn(IndexStatus(currentIndex = GREEN, otherIndexState = ABSENT))

      indexService.prepareIndexForRebuild()

      verify(indexQueueService).sendPopulateIndexMessage(any())
    }

    @Test
    fun `A telemetry event is sent`() {
      whenever(indexStatusService.getIndexStatus())
        .thenReturn(IndexStatus(currentIndex = GREEN, otherIndexState = ABSENT))

      indexService.prepareIndexForRebuild()

      verify(telemetryClient).trackEvent(TelemetryEvents.BUILDING_INDEX.name, mapOf("index" to "BLUE"), null)
    }

    @Test
    fun `The updated index is returned`() {
      val expectedIndexStatus = IndexStatus(currentIndex = GREEN, otherIndexState = BUILDING)
      whenever(indexStatusService.getIndexStatus())
        .thenReturn(IndexStatus(currentIndex = GREEN, otherIndexState = ABSENT))
        .thenReturn(expectedIndexStatus)

      val result = indexService.prepareIndexForRebuild()

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

      val result = indexService.markIndexingComplete(ignoreThreshold = true)

      verify(indexStatusService).getIndexStatus()
      result shouldBeLeft BuildNotInProgressError(expectedIndexStatus)
    }

    @Test
    fun `Index with active messages returns error`() {
      whenever(indexStatusService.getIndexStatus()).thenReturn(IndexStatus(currentIndex = GREEN, otherIndexState = BUILDING))
      val expectedIndexQueueStatus = IndexQueueStatus(1, 0, 0)
      whenever(indexQueueService.getIndexQueueStatus()).thenReturn(expectedIndexQueueStatus)

      val result = indexService.markIndexingComplete(ignoreThreshold = true)

      verify(indexStatusService).getIndexStatus()
      result shouldBeLeft ActiveMessagesExistError(BLUE, expectedIndexQueueStatus, "mark complete")
    }

    @Test
    fun `Index not reached threshold returns error`() {
      whenever(indexStatusService.getIndexStatus()).thenReturn(IndexStatus(currentIndex = GREEN, otherIndexState = BUILDING))
      val expectedIndexQueueStatus = IndexQueueStatus(0, 0, 0)
      whenever(indexQueueService.getIndexQueueStatus()).thenReturn(expectedIndexQueueStatus)
      whenever(indexBuildProperties.completeThreshold).thenReturn(1000000)

      val result = indexService.markIndexingComplete(ignoreThreshold = false)

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

      indexService.markIndexingComplete(ignoreThreshold = true)

      verify(indexStatusService).markBuildCompleteAndSwitchIndex()
    }

    @Test
    fun `Index not reached threshold but ignoring threshold - completes ok`() {
      whenever(indexStatusService.getIndexStatus()).thenReturn(IndexStatus(currentIndex = GREEN, otherIndexState = BUILDING))
      val expectedIndexQueueStatus = IndexQueueStatus(0, 0, 0)
      whenever(indexQueueService.getIndexQueueStatus()).thenReturn(expectedIndexQueueStatus)
      whenever(indexBuildProperties.completeThreshold).thenReturn(1000000)

      val result = indexService.markIndexingComplete(ignoreThreshold = false)

      verify(indexStatusService).getIndexStatus()
      verify(indexQueueService).getIndexQueueStatus()
      result shouldBeLeft ThresholdNotReachedError(BLUE, 1000000)
    }

    @Test
    fun `A request is made to switch alias`() {
      whenever(indexStatusService.getIndexStatus())
        .thenReturn(IndexStatus(currentIndex = GREEN, otherIndexState = BUILDING))
        .thenReturn(IndexStatus(currentIndex = BLUE, currentIndexState = COMPLETED))
      whenever(indexStatusService.markBuildCompleteAndSwitchIndex()).thenReturn(IndexStatus(currentIndex = BLUE, currentIndexState = COMPLETED))

      indexService.markIndexingComplete(ignoreThreshold = true)

      verify(prisonerSynchroniserService).switchAliasIndex(BLUE)
    }

    @Test
    fun `A telemetry event is sent`() {
      whenever(indexStatusService.getIndexStatus())
        .thenReturn(IndexStatus(currentIndex = GREEN, otherIndexState = BUILDING))
        .thenReturn(IndexStatus(currentIndex = BLUE, currentIndexState = COMPLETED))
      whenever(indexStatusService.markBuildCompleteAndSwitchIndex()).thenReturn(IndexStatus(currentIndex = BLUE, currentIndexState = COMPLETED))

      indexService.markIndexingComplete(ignoreThreshold = true)

      verify(telemetryClient).trackEvent(TelemetryEvents.COMPLETED_BUILDING_INDEX.name, mapOf("index" to "BLUE"), null)
    }

    @Test
    fun `Once current index marked as complete, the 'other' index is current`() {
      whenever(indexStatusService.getIndexStatus())
        .thenReturn(IndexStatus(currentIndex = GREEN, otherIndexState = BUILDING))
        .thenReturn(IndexStatus(currentIndex = BLUE, currentIndexState = COMPLETED))
      whenever(indexStatusService.markBuildCompleteAndSwitchIndex()).thenReturn(IndexStatus(currentIndex = BLUE, currentIndexState = COMPLETED))

      val result = indexService.markIndexingComplete(ignoreThreshold = true)

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

      val result = indexService.switchIndex(false)

      verify(indexStatusService).switchIndex()
      result shouldBeRight IndexStatus(currentIndex = BLUE, currentIndexState = COMPLETED)
    }

    @Test
    fun `A request is made to switch alias`() {
      whenever(indexStatusService.getIndexStatus())
        .thenReturn(IndexStatus(currentIndex = GREEN, otherIndexState = COMPLETED))
        .thenReturn(IndexStatus(currentIndex = BLUE, currentIndexState = COMPLETED))
      whenever(indexStatusService.switchIndex()).thenReturn(IndexStatus(currentIndex = BLUE, currentIndexState = COMPLETED))

      indexService.switchIndex(false)

      verify(prisonerSynchroniserService).switchAliasIndex(BLUE)
    }

    @Test
    fun `A telemetry event is sent`() {
      whenever(indexStatusService.getIndexStatus())
        .thenReturn(IndexStatus(currentIndex = GREEN, otherIndexState = COMPLETED))
        .thenReturn(IndexStatus(currentIndex = BLUE, currentIndexState = COMPLETED))
      whenever(indexStatusService.switchIndex()).thenReturn(IndexStatus(currentIndex = BLUE, currentIndexState = COMPLETED))

      indexService.switchIndex(false)

      verify(telemetryClient).trackEvent(TelemetryEvents.SWITCH_INDEX.name, mapOf("index" to "BLUE"), null)
    }

    @Test
    fun `Index building returns error`() {
      val expectedIndexStatus = IndexStatus(currentIndex = GREEN, otherIndexState = BUILDING)
      whenever(indexStatusService.getIndexStatus()).thenReturn(expectedIndexStatus)

      val result = indexService.switchIndex(false)

      verify(indexStatusService).getIndexStatus()
      result shouldBeLeft BuildInProgressError(expectedIndexStatus)
    }

    @Test
    fun `Index Cancelled returns error`() {
      val expectedIndexStatus = IndexStatus(currentIndex = GREEN, otherIndexState = CANCELLED)
      whenever(indexStatusService.getIndexStatus()).thenReturn(expectedIndexStatus)

      val result = indexService.switchIndex(false)

      verify(indexStatusService).getIndexStatus()
      result shouldBeLeft BuildCancelledError(expectedIndexStatus)
    }

    @Test
    fun `Index Absent returns error`() {
      val expectedIndexStatus = IndexStatus(currentIndex = GREEN, otherIndexState = ABSENT)
      whenever(indexStatusService.getIndexStatus()).thenReturn(expectedIndexStatus)

      val result = indexService.switchIndex(false)

      verify(indexStatusService).getIndexStatus()
      result shouldBeLeft BuildAbsentError(expectedIndexStatus)
    }
  }

  @Nested
  inner class CancelIndexing {

    @Test
    fun `Index not building returns error`() = runTest {
      val expectedIndexStatus = IndexStatus(currentIndex = GREEN, otherIndexState = COMPLETED)
      whenever(indexStatusService.getIndexStatus()).thenReturn(expectedIndexStatus)

      val result = indexService.cancelIndexing()

      verify(indexStatusService).getIndexStatus()
      result shouldBeLeft BuildNotInProgressError(expectedIndexStatus)
    }

    @Test
    fun `A request is made to mark the index state as cancelled`() = runTest {
      val expectedIndexStatus = IndexStatus(currentIndex = GREEN, otherIndexState = BUILDING)
      whenever(indexStatusService.getIndexStatus()).thenReturn(expectedIndexStatus)

      indexService.cancelIndexing()

      verify(indexStatusService).markBuildCancelled()
    }

    @Test
    fun `all messages are cleared`() = runTest {
      val expectedIndexStatus = IndexStatus(currentIndex = GREEN, otherIndexState = BUILDING)
      whenever(indexStatusService.getIndexStatus()).thenReturn(expectedIndexStatus)

      indexService.cancelIndexing()

      verify(hmppsQueueService).purgeQueue(
        check {
          assertThat(it.queueName).isEqualTo("index-queue")
        },
      )
    }

    @Test
    fun `A telemetry event is sent`() = runTest {
      val expectedIndexStatus = IndexStatus(currentIndex = GREEN, otherIndexState = BUILDING)
      whenever(indexStatusService.getIndexStatus()).thenReturn(expectedIndexStatus)

      indexService.cancelIndexing()

      verify(telemetryClient).trackEvent(TelemetryEvents.CANCELLED_BUILDING_INDEX.name, mapOf("index" to "BLUE"), null)
    }

    @Test
    fun `Once current index marked as cancelled, the 'other' index is current`() = runTest {
      val expectedIndexStatus = IndexStatus(currentIndex = GREEN, otherIndexState = CANCELLED)
      whenever(indexStatusService.getIndexStatus())
        .thenReturn(IndexStatus(currentIndex = GREEN, otherIndexState = BUILDING))
        .thenReturn(expectedIndexStatus)

      val result = indexService.cancelIndexing()

      verify(indexStatusService, times(2)).getIndexStatus()
      result shouldBeRight expectedIndexStatus
    }
  }

  @Nested
  inner class IndexOffender {
    @BeforeEach
    internal fun setUp() {
      whenever(prisonerSynchroniserService.synchronisePrisoner(any(), any())).thenReturn(Prisoner().right())
    }

    @Test
    internal fun `will delegate to synchronisation service`() {
      val indexStatus = IndexStatus(currentIndex = GREEN, currentIndexState = COMPLETED, otherIndexState = ABSENT)
      whenever(indexStatusService.getIndexStatus()).thenReturn(indexStatus)

      indexService.updatePrisoner("ABC123D")

      verify(prisonerSynchroniserService).synchronisePrisoner("ABC123D", GREEN)
    }
  }

  @Nested
  inner class PopulateIndex {

    @Test
    internal fun `will return an error if indexing is not in progress`() {
      val indexStatus = IndexStatus(currentIndex = GREEN, otherIndexState = COMPLETED)
      whenever(indexStatusService.getIndexStatus()).thenReturn(indexStatus)

      val result = indexService.populateIndex(GREEN)

      result shouldBeLeft BuildNotInProgressError(indexStatus)
    }

    @Test
    internal fun `will return an error if indexing request is for the wrong index`() {
      val indexStatus = IndexStatus(currentIndex = GREEN, currentIndexState = COMPLETED, otherIndexState = BUILDING)

      whenever(indexStatusService.getIndexStatus()).thenReturn(indexStatus)

      val result = indexService.populateIndex(GREEN)

      result shouldBeLeft WrongIndexRequestedError(indexStatus)
    }

    @Test
    internal fun `will return the number of chunks sent for processing`() {
      val indexStatus = IndexStatus(currentIndex = GREEN, currentIndexState = COMPLETED, otherIndexState = BUILDING)
      whenever(indexStatusService.getIndexStatus()).thenReturn(indexStatus)
      whenever(prisonerSynchroniserService.splitAllPrisonersIntoChunks()).thenReturn(
        listOf(
          PrisonerPage(1, 1000),
          PrisonerPage(2, 1000),
          PrisonerPage(3, 1000),
        ),
      )

      val result = indexService.populateIndex(BLUE)

      result shouldBeRight 3
    }

    @Test
    internal fun `For each chunk should send a process chunk message`() {
      val indexStatus = IndexStatus(currentIndex = GREEN, currentIndexState = COMPLETED, otherIndexState = BUILDING)
      whenever(indexStatusService.getIndexStatus()).thenReturn(indexStatus)
      whenever(prisonerSynchroniserService.splitAllPrisonersIntoChunks()).thenReturn(
        listOf(
          PrisonerPage(1, 1000),
          PrisonerPage(2, 1000),
          PrisonerPage(3, 1000),
        ),
      )

      indexService.populateIndex(BLUE)

      verify(indexQueueService).sendPopulatePrisonerPageMessage(PrisonerPage(1, 1000))
      verify(indexQueueService).sendPopulatePrisonerPageMessage(PrisonerPage(2, 1000))
      verify(indexQueueService).sendPopulatePrisonerPageMessage(PrisonerPage(3, 1000))
    }
  }

  @Nested
  inner class PopulateIndexWithPrisonerPage {
    @BeforeEach
    internal fun setUp() {
      whenever(indexStatusService.getIndexStatus()).thenReturn(IndexStatus(currentIndex = GREEN, currentIndexState = COMPLETED, otherIndexState = BUILDING))
      whenever(prisonerSynchroniserService.getAllPrisonerNumbersInPage(any()))
        .thenReturn(listOf(OffenderId("ABC123D"), OffenderId("A12345")))
    }

    @Test
    internal fun `will get offenders in the supplied page`() {
      indexService.populateIndexWithPrisonerPage(PrisonerPage(page = 99, pageSize = 1000))

      verify(prisonerSynchroniserService).getAllPrisonerNumbersInPage(PrisonerPage(page = 99, pageSize = 1000))
    }

    @Test
    internal fun `for each offender will send populate offender message`() {
      indexService.populateIndexWithPrisonerPage(PrisonerPage(page = 99, pageSize = 1000))

      verify(indexQueueService).sendPopulatePrisonerMessage("ABC123D")
      verify(indexQueueService).sendPopulatePrisonerMessage("A12345")
    }
  }

  @Nested
  inner class PopulateIndexWithPrisoner {
    private val prisoner = Prisoner().also { it.prisonerNumber = "ABC123D" }

    @BeforeEach
    internal fun setUp() {
      whenever(prisonerSynchroniserService.synchronisePrisoner(any(), any())).thenReturn(prisoner.right())
    }

    @Test
    internal fun `will return error if other index is not building`() {
      val indexStatus = IndexStatus(currentIndex = GREEN, currentIndexState = COMPLETED, otherIndexState = COMPLETED)
      whenever(indexStatusService.getIndexStatus()).thenReturn(indexStatus)

      val result = indexService.populateIndexWithPrisoner("ABC123D")

      result shouldBeLeft BuildNotInProgressError(indexStatus)
    }

    @Test
    internal fun `will return offender just indexed`() {
      val indexStatus = IndexStatus(currentIndex = GREEN, currentIndexState = COMPLETED, otherIndexState = BUILDING)
      whenever(indexStatusService.getIndexStatus()).thenReturn(indexStatus)

      val result = indexService.populateIndexWithPrisoner("ABC123D")

      result.map { it.prisonerNumber } shouldBeRight "ABC123D"
    }

    @Test
    internal fun `will synchronise offender to current building index`() {
      val indexStatus = IndexStatus(currentIndex = GREEN, currentIndexState = COMPLETED, otherIndexState = BUILDING)
      whenever(indexStatusService.getIndexStatus()).thenReturn(indexStatus)

      indexService.populateIndexWithPrisoner("ABC123D")

      verify(prisonerSynchroniserService).synchronisePrisoner("ABC123D", BLUE)
    }
  }

  @Nested
  inner class CountIndex {
    @Test
    fun `Should return count from elasticsearch client`() {
      whenever(elasticSearchClient.count(any(), any())).thenReturn(CountResponse(10L, null, null))

      assertThat(indexService.getIndexCount(BLUE)).isEqualTo(10L)
    }

    @Test
    fun `Should return negative count from elasticsearch client for missing index`() {
      whenever(elasticSearchClient.count(any(), any())).thenThrow(OpenSearchStatusException("no such index [probation-search-green]", null, null))

      assertThat(indexService.getIndexCount(BLUE)).isEqualTo(-1L)
    }
  }

  @Nested
  inner class UpdatePrisoner {
    @Test
    fun `No active indexes, update is not requested`() {
      whenever(indexStatusService.getIndexStatus()).thenReturn(IndexStatus.newIndex())

      indexService.updatePrisoner("SOME_CRN")

      verifyNoInteractions(prisonerSynchroniserService)
    }

    @Test
    fun `No active indexes, error is returned`() {
      val indexStatus = IndexStatus.newIndex()
      whenever(indexStatusService.getIndexStatus()).thenReturn(indexStatus)

      val result = indexService.updatePrisoner("SOME_CRN")

      result shouldBeLeft NoActiveIndexesError(indexStatus)
    }

    @Test
    fun `Current index active, offender is updated`() {
      val indexStatus = IndexStatus(currentIndex = GREEN, currentIndexState = COMPLETED)
      whenever(indexStatusService.getIndexStatus()).thenReturn(indexStatus)
      whenever(prisonerSynchroniserService.synchronisePrisoner(any(), any())).thenReturn(Prisoner().right())

      indexService.updatePrisoner("SOME_CRN")

      verify(prisonerSynchroniserService).synchronisePrisoner("SOME_CRN", indexStatus.currentIndex)
    }

    @Test
    fun `Other index active, offender is updated`() {
      val indexStatus = IndexStatus(currentIndex = NONE, otherIndexState = BUILDING, currentIndexState = ABSENT)
      whenever(indexStatusService.getIndexStatus()).thenReturn(indexStatus)
      whenever(prisonerSynchroniserService.synchronisePrisoner(any(), any())).thenReturn(Prisoner().right())

      indexService.updatePrisoner("SOME_CRN")

      verify(prisonerSynchroniserService).synchronisePrisoner("SOME_CRN", indexStatus.otherIndex)
    }

    @Test
    fun `Both indexes active, offender is updated on both indexes`() {
      val indexStatus = IndexStatus(currentIndex = GREEN, otherIndexState = BUILDING, currentIndexState = COMPLETED)
      whenever(indexStatusService.getIndexStatus()).thenReturn(indexStatus)
      whenever(prisonerSynchroniserService.synchronisePrisoner(any(), any())).thenReturn(Prisoner().right())

      indexService.updatePrisoner("SOME_CRN")

      verify(prisonerSynchroniserService).synchronisePrisoner(eq("SOME_CRN"), eq(GREEN), eq(BLUE))
    }
  }
}
