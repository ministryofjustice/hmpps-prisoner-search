package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import com.microsoft.applicationinsights.TelemetryClient
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.IndexBuildProperties
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.TelemetryEvents
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.model.OffenderBookingBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.model.IndexState.BUILDING
import uk.gov.justice.digital.hmpps.prisonersearch.model.IndexState.COMPLETED
import uk.gov.justice.digital.hmpps.prisonersearch.model.IndexStatus
import uk.gov.justice.digital.hmpps.prisonersearch.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.model.SyncIndex.BLUE
import uk.gov.justice.digital.hmpps.prisonersearch.model.SyncIndex.GREEN
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import java.util.concurrent.CompletableFuture

class PopulateIndexServiceTest {

  private val indexStatusService = mock<IndexStatusService>()
  private val prisonerSynchroniserService = mock<PrisonerSynchroniserService>()
  private val indexQueueService = mock<IndexQueueService>()
  private val maintainIndexService = mock<MaintainIndexService>()
  private val hmppsQueueService = mock<HmppsQueueService>()
  private val nomisService = mock<NomisService>()
  private val telemetryClient = mock<TelemetryClient>()
  private val indexBuildProperties = IndexBuildProperties(10, 10)

  private val populateIndexService = PopulateIndexService(
    indexStatusService,
    prisonerSynchroniserService,
    indexQueueService,
    maintainIndexService,
    nomisService,
    telemetryClient,
    indexBuildProperties,
  )

  private val indexSqsClient = mock<SqsAsyncClient>()
  private val indexSqsDlqClient = mock<SqsAsyncClient>()

  init {
    whenever(hmppsQueueService.findByQueueId("index")).thenReturn(
      HmppsQueue(
        "index",
        indexSqsClient,
        "index-queue",
        indexSqsDlqClient,
        "index-dlq",
      ),
    )
    whenever(indexSqsClient.getQueueUrl(any<GetQueueUrlRequest>())).thenReturn(
      CompletableFuture.completedFuture(
        GetQueueUrlResponse.builder().queueUrl("arn:eu-west-1:index-queue").build(),
      ),
    )
    whenever(indexSqsDlqClient.getQueueUrl(any<GetQueueUrlRequest>())).thenReturn(
      CompletableFuture.completedFuture(
        GetQueueUrlResponse.builder().queueUrl("arn:eu-west-1:index-dlq").build(),
      ),
    )
  }

  @Nested
  inner class PopulateIndex {
    private val indexStatus =
      IndexStatus(currentIndex = GREEN, currentIndexState = COMPLETED, otherIndexState = BUILDING)

    @BeforeEach
    internal fun beforeEach() {
      whenever(indexStatusService.getIndexStatus()).thenReturn(indexStatus)
    }

    @Test
    internal fun `will return an error if indexing is not in progress`() {
      val indexStatus = IndexStatus(currentIndex = GREEN, otherIndexState = COMPLETED)
      whenever(indexStatusService.getIndexStatus()).thenReturn(indexStatus)

      val result = populateIndexService.populateIndex(GREEN)

      result shouldBeLeft BuildNotInProgressError(indexStatus)
    }

    @Test
    internal fun `will return an error if indexing request is for the wrong index`() {
      val result = populateIndexService.populateIndex(GREEN)

      result shouldBeLeft WrongIndexRequestedError(indexStatus)
    }

    @Test
    internal fun `will return the number of chunks sent for processing`() {
      whenever(nomisService.getTotalNumberOfPrisoners()).thenReturn(25)

      val result = populateIndexService.populateIndex(BLUE)

      result shouldBeRight 3
    }

    @Test
    internal fun `For each chunk should send a process chunk message`() {
      whenever(nomisService.getTotalNumberOfPrisoners()).thenReturn(25)

      populateIndexService.populateIndex(BLUE)

      verify(indexQueueService).sendPopulatePrisonerPageMessage(PrisonerPage(0, 10))
      verify(indexQueueService).sendPopulatePrisonerPageMessage(PrisonerPage(1, 10))
      verify(indexQueueService).sendPopulatePrisonerPageMessage(PrisonerPage(2, 10))
    }

    @Test
    internal fun `will split total list by our page size`() {
      whenever(nomisService.getTotalNumberOfPrisoners()).thenReturn(30)

      populateIndexService.populateIndex(BLUE)

      verify(indexQueueService).sendPopulatePrisonerPageMessage(PrisonerPage(0, 10))
      verify(indexQueueService).sendPopulatePrisonerPageMessage(PrisonerPage(1, 10))
      verify(indexQueueService).sendPopulatePrisonerPageMessage(PrisonerPage(2, 10))
    }

    @Test
    internal fun `will round up last page to page size when over`() {
      whenever(nomisService.getTotalNumberOfPrisoners()).thenReturn(31)

      populateIndexService.populateIndex(BLUE)

      verify(indexQueueService).sendPopulatePrisonerPageMessage(PrisonerPage(0, 10))
      verify(indexQueueService).sendPopulatePrisonerPageMessage(PrisonerPage(1, 10))
      verify(indexQueueService).sendPopulatePrisonerPageMessage(PrisonerPage(2, 10))
      verify(indexQueueService).sendPopulatePrisonerPageMessage(PrisonerPage(3, 10))
      verifyNoMoreInteractions(indexQueueService)
    }

    @Test
    internal fun `will round up last page to page size when under`() {
      whenever(nomisService.getTotalNumberOfPrisoners()).thenReturn(29)

      populateIndexService.populateIndex(BLUE)
      verify(indexQueueService).sendPopulatePrisonerPageMessage(PrisonerPage(0, 10))
      verify(indexQueueService).sendPopulatePrisonerPageMessage(PrisonerPage(1, 10))
      verify(indexQueueService).sendPopulatePrisonerPageMessage(PrisonerPage(2, 10))
      verifyNoMoreInteractions(indexQueueService)
    }

    @Test
    internal fun `will create a large number of pages for a large number of prisoners`() {
      whenever(nomisService.getTotalNumberOfPrisoners()).thenReturn(20001)

      populateIndexService.populateIndex(BLUE)
      verify(indexQueueService, times(2001)).sendPopulatePrisonerPageMessage(any())
    }

    @Test
    internal fun `will create a single pages for a tiny number of prisoners`() {
      whenever(nomisService.getTotalNumberOfPrisoners()).thenReturn(1)

      populateIndexService.populateIndex(BLUE)
      verify(indexQueueService).sendPopulatePrisonerPageMessage(PrisonerPage(0, 10))
      verifyNoMoreInteractions(indexQueueService)
    }

    @Test
    internal fun `will create no pages for no prisoners`() {
      whenever(nomisService.getTotalNumberOfPrisoners()).thenReturn(0)

      populateIndexService.populateIndex(BLUE)
      verifyNoMoreInteractions(indexQueueService)
    }

    @Test
    internal fun `Should create a telemetry event`() {
      whenever(nomisService.getTotalNumberOfPrisoners()).thenReturn(25)

      populateIndexService.populateIndex(BLUE)

      verify(telemetryClient).trackEvent(
        uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.TelemetryEvents.POPULATE_PRISONER_PAGES.name,
        mapOf("totalNumberOfPrisoners" to "25", "pageSize" to "10"),
        null,
      )
    }
  }

  @Nested
  inner class PopulateIndexWithPrisonerPage {
    @BeforeEach
    internal fun setUp() {
      whenever(indexStatusService.getIndexStatus()).thenReturn(
        IndexStatus(
          currentIndex = GREEN,
          currentIndexState = COMPLETED,
          otherIndexState = BUILDING,
        ),
      )
      whenever(nomisService.getPrisonerNumbers(any(), any()))
        .thenReturn(listOf("ABC123D", "A12345"))
    }

    @Test
    internal fun `will get offenders in the supplied page`() {
      populateIndexService.populateIndexWithPrisonerPage(PrisonerPage(page = 99, pageSize = 1000))

      verify(nomisService).getPrisonerNumbers(page = 99, pageSize = 1000)
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
      whenever(prisonerSynchroniserService.index(any(), any())).thenReturn(prisoner)
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
      val booking = OffenderBookingBuilder().anOffenderBooking()
      whenever(nomisService.getOffender(any())).thenReturn(booking)

      val result = populateIndexService.populateIndexWithPrisoner("ABC123D")

      result.map { it.prisonerNumber } shouldBeRight "ABC123D"
    }

    @Test
    internal fun `will synchronise offender to current building index`() {
      val indexStatus = IndexStatus(currentIndex = GREEN, currentIndexState = COMPLETED, otherIndexState = BUILDING)
      whenever(indexStatusService.getIndexStatus()).thenReturn(indexStatus)
      val booking = OffenderBookingBuilder().anOffenderBooking()
      whenever(nomisService.getOffender(any())).thenReturn(booking)

      populateIndexService.populateIndexWithPrisoner("ABC123D")

      verify(prisonerSynchroniserService).index(booking, BLUE)
      verify(nomisService).getOffender("ABC123D")
    }

    @Test
    internal fun `will return not found if prisoner not in NOMIS`() {
      val indexStatus = IndexStatus(currentIndex = GREEN, currentIndexState = COMPLETED, otherIndexState = BUILDING)
      whenever(indexStatusService.getIndexStatus()).thenReturn(indexStatus)

      val result = populateIndexService.populateIndexWithPrisoner("ABC123D")

      result shouldBeLeft PrisonerNotFoundError("ABC123D")
      verifyNoInteractions(prisonerSynchroniserService)
    }

    @Test
    internal fun `will raise the not found event if prisoner not in NOMIS`() {
      val indexStatus = IndexStatus(currentIndex = GREEN, currentIndexState = COMPLETED, otherIndexState = BUILDING)
      whenever(indexStatusService.getIndexStatus()).thenReturn(indexStatus)

      populateIndexService.populateIndexWithPrisoner("ABC123D")
      verify(telemetryClient).trackEvent(uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.TelemetryEvents.BUILD_PRISONER_NOT_FOUND.name, mapOf("prisonerNumber" to "ABC123D"), null)
    }
  }
}
