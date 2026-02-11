@file:OptIn(ExperimentalCoroutinesApi::class)

package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IndexState.BUILDING
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IndexState.CANCELLED
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IndexState.COMPLETED
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IndexStatus
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.TelemetryEvents
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners.IndexRequestType.POPULATE_INDEX
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.model.OffenderBookingBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.repository.PrisonerRepository
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import java.util.concurrent.CompletableFuture

class MaintainIndexServiceTest {

  private val indexStatusService = mock<IndexStatusService>()
  private val prisonerSynchroniserService = mock<PrisonerSynchroniserService>()
  private val indexQueueService = mock<IndexQueueService>()
  private val prisonerRepository = mock<PrisonerRepository>()
  private val hmppsQueueService = mock<HmppsQueueService>()
  private val nomisService = mock<NomisService>()
  private val telemetryClient = mock<TelemetryClient>()
  private val maintainIndexService = MaintainIndexService(indexStatusService, prisonerSynchroniserService, indexQueueService, prisonerRepository, hmppsQueueService, nomisService, telemetryClient)

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
    fun setUp() {
      whenever(indexQueueService.getIndexQueueStatus()).thenReturn(IndexQueueStatus(0, 0, 0))
    }

    @Test
    fun `Index already building returns error`() {
      val expectedIndexStatus = IndexStatus(currentIndexState = BUILDING)
      whenever(indexStatusService.getIndexStatus()).thenReturn(expectedIndexStatus)

      assertThatThrownBy { maintainIndexService.prepareIndexForRebuild() }
        .isInstanceOf(BuildAlreadyInProgressException::class.java)
        .hasMessageContaining("The build is already BUILDING")

      verify(indexStatusService).getIndexStatus()
    }

    @Test
    fun `Index has active messages returns an error`() {
      whenever(indexStatusService.getIndexStatus()).thenReturn(IndexStatus(currentIndexState = COMPLETED))
      val expectedIndexQueueStatus = IndexQueueStatus(1, 0, 0)
      whenever(indexQueueService.getIndexQueueStatus()).thenReturn(expectedIndexQueueStatus)

      assertThatThrownBy { maintainIndexService.prepareIndexForRebuild() }
        .isInstanceOf(ActiveMessagesExistException::class.java)
        .hasMessageContaining("The index has active messages")
    }

    @Test
    fun `A request is made to mark the index build is in progress`() {
      whenever(indexStatusService.getIndexStatus())
        .thenReturn(IndexStatus(currentIndexState = COMPLETED))

      maintainIndexService.prepareIndexForRebuild()

      verify(indexStatusService).markBuildInProgress()
    }

    @Test
    fun `A request is made to build the index`() {
      whenever(indexStatusService.getIndexStatus())
        .thenReturn(IndexStatus(currentIndexState = COMPLETED))

      maintainIndexService.prepareIndexForRebuild()

      verify(indexQueueService).sendIndexMessage(POPULATE_INDEX)
    }

    @Test
    fun `A telemetry event is sent`() {
      whenever(indexStatusService.getIndexStatus())
        .thenReturn(IndexStatus(currentIndexState = COMPLETED))

      maintainIndexService.prepareIndexForRebuild()

      verify(telemetryClient).trackEvent(TelemetryEvents.BUILDING_INDEX.name, mapOf("index-state" to "COMPLETED"), null)
    }

    @Test
    fun `The updated index is returned`() {
      val expectedIndexStatus = IndexStatus(currentIndexState = BUILDING)
      whenever(indexStatusService.getIndexStatus())
        .thenReturn(IndexStatus(currentIndexState = COMPLETED))
        .thenReturn(expectedIndexStatus)

      val result = maintainIndexService.prepareIndexForRebuild()

      verify(indexStatusService, times(2)).getIndexStatus()
      assertThat(result).isEqualTo(expectedIndexStatus)
    }
  }

  @Nested
  inner class MarkIndexingComplete {
    @BeforeEach
    fun setUp() {
      whenever(indexStatusService.markBuildComplete()).thenReturn(IndexStatus(currentIndexState = COMPLETED))
      whenever(indexQueueService.getIndexQueueStatus()).thenReturn(IndexQueueStatus(0, 0, 0))
    }

    @Test
    fun `Index not building returns error`() {
      val expectedIndexStatus = IndexStatus(currentIndexState = COMPLETED)
      whenever(indexStatusService.getIndexStatus()).thenReturn(expectedIndexStatus)

      assertThatThrownBy { maintainIndexService.markIndexingComplete() }
        .isInstanceOf(BuildNotInProgressException::class.java)
        .hasMessageContaining("The index is in state COMPLETED")

      verify(indexStatusService).getIndexStatus()
    }

    @Test
    fun `Index with active messages returns error`() {
      whenever(indexStatusService.getIndexStatus()).thenReturn(IndexStatus(currentIndexState = BUILDING))
      val expectedIndexQueueStatus = IndexQueueStatus(1, 0, 0)
      whenever(indexQueueService.getIndexQueueStatus()).thenReturn(expectedIndexQueueStatus)

      assertThatThrownBy { maintainIndexService.markIndexingComplete() }
        .isInstanceOf(ActiveMessagesExistException::class.java)
        .hasMessageContaining("The index has active messages")

      verify(indexStatusService).getIndexStatus()
    }

    @Test
    fun `A request is made to mark the index state as complete`() {
      whenever(indexStatusService.getIndexStatus())
        .thenReturn(IndexStatus(currentIndexState = BUILDING))
        .thenReturn(IndexStatus(currentIndexState = COMPLETED))
      whenever(indexStatusService.markBuildComplete()).thenReturn(IndexStatus(currentIndexState = COMPLETED))

      maintainIndexService.markIndexingComplete()

      verify(indexStatusService).markBuildComplete()
    }

    @Test
    fun `A telemetry event is sent`() {
      whenever(indexStatusService.getIndexStatus())
        .thenReturn(IndexStatus(currentIndexState = BUILDING))
        .thenReturn(IndexStatus(currentIndexState = COMPLETED))
      whenever(indexStatusService.markBuildComplete()).thenReturn(IndexStatus(currentIndexState = COMPLETED))

      maintainIndexService.markIndexingComplete()

      verify(telemetryClient).trackEvent(
        TelemetryEvents.COMPLETED_BUILDING_INDEX.name,
        mapOf("index-state" to "IndexStatus(id=STATUS, currentIndexStartBuildTime=null, currentIndexEndBuildTime=null, currentIndexState=COMPLETED)"),
        null,
      )
    }

    @Test
    fun `Once current index marked as complete, the 'other' index is current`() {
      whenever(indexStatusService.getIndexStatus())
        .thenReturn(IndexStatus(currentIndexState = BUILDING))
        .thenReturn(IndexStatus(currentIndexState = COMPLETED))
      whenever(indexStatusService.markBuildComplete()).thenReturn(IndexStatus(currentIndexState = COMPLETED))

      val result = maintainIndexService.markIndexingComplete()

      verify(indexStatusService, times(2)).getIndexStatus()
      assertThat(result).isEqualTo(IndexStatus(currentIndexState = COMPLETED))
    }
  }

  @Nested
  inner class CancelIndexing {

    @Test
    fun `Index not building returns error`() {
      val expectedIndexStatus = IndexStatus(currentIndexState = COMPLETED)
      whenever(indexStatusService.getIndexStatus()).thenReturn(expectedIndexStatus)

      assertThatThrownBy { maintainIndexService.cancelIndexing() }
        .isInstanceOf(BuildNotInProgressException::class.java)
        .hasMessageContaining("The index is in state COMPLETED")

      verify(indexStatusService).getIndexStatus()
    }

    @Test
    fun `A request is made to mark the index state as cancelled`() {
      val expectedIndexStatus = IndexStatus(currentIndexState = BUILDING)
      whenever(indexStatusService.getIndexStatus()).thenReturn(expectedIndexStatus)

      maintainIndexService.cancelIndexing()

      verify(indexStatusService).markBuildCancelled()
    }

    @Test
    fun `all messages are cleared`() = runTest {
      val expectedIndexStatus = IndexStatus(currentIndexState = BUILDING)
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
      val expectedIndexStatus = IndexStatus(currentIndexState = BUILDING)
      whenever(indexStatusService.getIndexStatus()).thenReturn(expectedIndexStatus)

      maintainIndexService.cancelIndexing()

      verify(telemetryClient).trackEvent(TelemetryEvents.CANCELLED_BUILDING_INDEX.name, mapOf("index-state" to "BUILDING"), null)
    }

    @Test
    fun `Once current index marked as cancelled, the 'other' index is current`() {
      val expectedIndexStatus = IndexStatus(currentIndexState = CANCELLED)
      whenever(indexStatusService.getIndexStatus())
        .thenReturn(IndexStatus(currentIndexState = BUILDING))
        .thenReturn(expectedIndexStatus)

      val result = maintainIndexService.cancelIndexing()

      verify(indexStatusService, times(2)).getIndexStatus()
      assertThat(result).isEqualTo(expectedIndexStatus)
    }
  }

  @Nested
  inner class IndexOffender {
    @Test
    fun `will delegate to synchronisation service if prisoner found in NOMIS`() {
      val booking = OffenderBookingBuilder().anOffenderBooking()
      whenever(prisonerSynchroniserService.reindexUpdate(any(), any()))
        .thenReturn(
          Prisoner().apply {
            prisonerNumber = booking.offenderNo
          },
        )
      val indexStatus = IndexStatus(currentIndexState = COMPLETED)
      whenever(indexStatusService.getIndexStatus()).thenReturn(indexStatus)
      whenever(nomisService.getOffender(booking.offenderNo)).thenReturn(booking)

      maintainIndexService.indexPrisoner(booking.offenderNo)

      verify(prisonerSynchroniserService).reindexUpdate(booking, "MAINTAIN")
      verify(prisonerSynchroniserService).reindexIncentive(booking.offenderNo, "MAINTAIN")
      verify(prisonerSynchroniserService).reindexRestrictedPatient(booking.offenderNo, booking, null, "MAINTAIN")
    }

    @Test
    fun `will delete from index if prisoner only found in indices`() {
      val indexStatus = IndexStatus(currentIndexState = COMPLETED)
      whenever(indexStatusService.getIndexStatus()).thenReturn(indexStatus)
      whenever(prisonerRepository.get(any())).thenReturn(Prisoner())

      maintainIndexService.indexPrisoner("ABC123D")

      verify(prisonerSynchroniserService).delete("ABC123D")
    }

    @Test
    fun `will raise a telemetry event if prisoner not found in NOMIS or indices`() {
      val indexStatus = IndexStatus(currentIndexState = COMPLETED)
      whenever(indexStatusService.getIndexStatus()).thenReturn(indexStatus)

      assertThatThrownBy { maintainIndexService.indexPrisoner("ABC123D") }
        .isInstanceOf(PrisonerNotFoundException::class.java)

      verify(telemetryClient).trackEvent(TelemetryEvents.PRISONER_NOT_FOUND.name, mapOf("prisonerNumber" to "ABC123D"), null)
    }

    @Test
    fun `will return the not found if prisoner not found in NOMIS or indices`() {
      val indexStatus = IndexStatus(currentIndexState = COMPLETED)
      whenever(indexStatusService.getIndexStatus()).thenReturn(indexStatus)

      assertThatThrownBy { maintainIndexService.indexPrisoner("ABC123D") }
        .isInstanceOf(PrisonerNotFoundException::class.java)
        .hasMessageContaining("The prisoner ABC123D")
    }

    @Test
    fun `Offender is updated but prisoner not found`() {
      val booking = OffenderBookingBuilder().anOffenderBooking()
      whenever(nomisService.getOffender(any())).thenReturn(booking)

      assertThatThrownBy { maintainIndexService.indexPrisoner("SOME_CRN") }
        .isInstanceOf(PrisonerNotFoundException::class.java)

      verify(prisonerSynchroniserService).reindexUpdate(booking, "MAINTAIN")
    }
  }
}
