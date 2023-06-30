package uk.gov.justice.digital.hmpps.prisonersearchindexer.services

import arrow.core.right
import com.microsoft.applicationinsights.TelemetryClient
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse
import uk.gov.justice.digital.hmpps.prisonersearchindexer.model.IndexState.BUILDING
import uk.gov.justice.digital.hmpps.prisonersearchindexer.model.IndexState.COMPLETED
import uk.gov.justice.digital.hmpps.prisonersearchindexer.model.IndexStatus
import uk.gov.justice.digital.hmpps.prisonersearchindexer.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearchindexer.model.SyncIndex.BLUE
import uk.gov.justice.digital.hmpps.prisonersearchindexer.model.SyncIndex.GREEN
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import java.util.concurrent.CompletableFuture

class PopulateIndexServiceTest {

  private val indexStatusService = mock<IndexStatusService>()
  private val prisonerSynchroniserService = mock<PrisonerSynchroniserService>()
  private val indexQueueService = mock<IndexQueueService>()
  private val maintainIndexService = mock<MaintainIndexService>()
  private val hmppsQueueService = mock<HmppsQueueService>()
  private val telemetryClient = mock<TelemetryClient>()
  private val populateIndexService = PopulateIndexService(indexStatusService, prisonerSynchroniserService, indexQueueService, maintainIndexService, telemetryClient)

  private val indexSqsClient = mock<SqsAsyncClient>()
  private val indexSqsDlqClient = mock<SqsAsyncClient>()

  init {
    whenever(hmppsQueueService.findByQueueId("index")).thenReturn(HmppsQueue("index", indexSqsClient, "index-queue", indexSqsDlqClient, "index-dlq"))
    whenever(indexSqsClient.getQueueUrl(any<GetQueueUrlRequest>())).thenReturn(CompletableFuture.completedFuture(GetQueueUrlResponse.builder().queueUrl("arn:eu-west-1:index-queue").build()))
    whenever(indexSqsDlqClient.getQueueUrl(any<GetQueueUrlRequest>())).thenReturn(CompletableFuture.completedFuture(GetQueueUrlResponse.builder().queueUrl("arn:eu-west-1:index-dlq").build()))
  }

  @Nested
  inner class PopulateIndex {

    @Test
    internal fun `will return an error if indexing is not in progress`() {
      val indexStatus = IndexStatus(currentIndex = GREEN, otherIndexState = COMPLETED)
      whenever(indexStatusService.getIndexStatus()).thenReturn(indexStatus)

      val result = populateIndexService.populateIndex(GREEN)

      result shouldBeLeft BuildNotInProgressError(indexStatus)
    }

    @Test
    internal fun `will return an error if indexing request is for the wrong index`() {
      val indexStatus = IndexStatus(currentIndex = GREEN, currentIndexState = COMPLETED, otherIndexState = BUILDING)

      whenever(indexStatusService.getIndexStatus()).thenReturn(indexStatus)

      val result = populateIndexService.populateIndex(GREEN)

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

      val result = populateIndexService.populateIndex(BLUE)

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

      populateIndexService.populateIndex(BLUE)

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
        .thenReturn(listOf("ABC123D", "A12345"))
    }

    @Test
    internal fun `will get offenders in the supplied page`() {
      populateIndexService.populateIndexWithPrisonerPage(PrisonerPage(page = 99, pageSize = 1000))

      verify(prisonerSynchroniserService).getAllPrisonerNumbersInPage(PrisonerPage(page = 99, pageSize = 1000))
    }

    @Test
    internal fun `for each offender will send populate offender message`() {
      populateIndexService.populateIndexWithPrisonerPage(PrisonerPage(page = 99, pageSize = 1000))

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

      val result = populateIndexService.populateIndexWithPrisoner("ABC123D")

      result shouldBeLeft BuildNotInProgressError(indexStatus)
    }

    @Test
    internal fun `will return offender just indexed`() {
      val indexStatus = IndexStatus(currentIndex = GREEN, currentIndexState = COMPLETED, otherIndexState = BUILDING)
      whenever(indexStatusService.getIndexStatus()).thenReturn(indexStatus)

      val result = populateIndexService.populateIndexWithPrisoner("ABC123D")

      result.map { it.prisonerNumber } shouldBeRight "ABC123D"
    }

    @Test
    internal fun `will synchronise offender to current building index`() {
      val indexStatus = IndexStatus(currentIndex = GREEN, currentIndexState = COMPLETED, otherIndexState = BUILDING)
      whenever(indexStatusService.getIndexStatus()).thenReturn(indexStatus)

      populateIndexService.populateIndexWithPrisoner("ABC123D")

      verify(prisonerSynchroniserService).synchronisePrisoner("ABC123D", BLUE)
    }
  }
}
