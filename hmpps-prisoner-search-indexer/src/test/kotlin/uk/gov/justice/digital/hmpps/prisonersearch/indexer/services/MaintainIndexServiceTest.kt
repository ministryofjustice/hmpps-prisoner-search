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
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IndexState.ABSENT
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IndexState.BUILDING
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IndexState.CANCELLED
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IndexState.COMPLETED
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IndexStatus
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.SyncIndex.BLUE
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.SyncIndex.GREEN
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.SyncIndex.NONE
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.IndexBuildProperties
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.TelemetryEvents
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
  private val indexBuildProperties = mock<IndexBuildProperties>()
  private val maintainIndexService = MaintainIndexService(indexStatusService, prisonerSynchroniserService, indexQueueService, prisonerRepository, hmppsQueueService, nomisService, telemetryClient, indexBuildProperties)

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

      assertThatThrownBy { maintainIndexService.prepareIndexForRebuild() }
        .isInstanceOf(BuildAlreadyInProgressException::class.java)
        .hasMessageContaining("The build for BLUE is already BUILDING")

      verify(indexStatusService).getIndexStatus()
    }

    @Test
    fun `Index has active messages returns an error`() {
      whenever(indexStatusService.getIndexStatus()).thenReturn(IndexStatus(currentIndex = GREEN, otherIndexState = COMPLETED))
      val expectedIndexQueueStatus = IndexQueueStatus(1, 0, 0)
      whenever(indexQueueService.getIndexQueueStatus()).thenReturn(expectedIndexQueueStatus)

      assertThatThrownBy { maintainIndexService.prepareIndexForRebuild() }
        .isInstanceOf(ActiveMessagesExistException::class.java)
        .hasMessageContaining("The index prisoner-search-blue has active messages")
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

      verify(indexQueueService).sendIndexMessage(any())
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
      assertThat(result).isEqualTo(expectedIndexStatus)
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

      assertThatThrownBy { maintainIndexService.markIndexingComplete(ignoreThreshold = true) }
        .isInstanceOf(BuildNotInProgressException::class.java)
        .hasMessageContaining("The index BLUE is in state COMPLETED")

      verify(indexStatusService).getIndexStatus()
    }

    @Test
    fun `Index with active messages returns error`() {
      whenever(indexStatusService.getIndexStatus()).thenReturn(IndexStatus(currentIndex = GREEN, otherIndexState = BUILDING))
      val expectedIndexQueueStatus = IndexQueueStatus(1, 0, 0)
      whenever(indexQueueService.getIndexQueueStatus()).thenReturn(expectedIndexQueueStatus)

      assertThatThrownBy { maintainIndexService.markIndexingComplete(ignoreThreshold = true) }
        .isInstanceOf(ActiveMessagesExistException::class.java)
        .hasMessageContaining("The index prisoner-search-blue has active messages")

      verify(indexStatusService).getIndexStatus()
    }

    @Test
    fun `Index not reached threshold returns error`() {
      whenever(indexStatusService.getIndexStatus()).thenReturn(IndexStatus(currentIndex = GREEN, otherIndexState = BUILDING))
      val expectedIndexQueueStatus = IndexQueueStatus(0, 0, 0)
      whenever(indexQueueService.getIndexQueueStatus()).thenReturn(expectedIndexQueueStatus)
      whenever(indexBuildProperties.completeThreshold).thenReturn(10000)
      whenever(prisonerRepository.count(any())).thenReturn(9999)

      assertThatThrownBy { maintainIndexService.markIndexingComplete(ignoreThreshold = false) }
        .isInstanceOf(ThresholdNotReachedException::class.java)
        .hasMessageContaining("The index prisoner-search-blue has not reached threshold 10000 so")

      verify(indexStatusService).getIndexStatus()
      verify(indexQueueService).getIndexQueueStatus()
      verify(prisonerRepository).count(BLUE)
    }

    @Test
    fun `Index reached threshold with other index under threshold`() {
      whenever(indexStatusService.getIndexStatus()).thenReturn(IndexStatus(currentIndex = GREEN, otherIndexState = BUILDING))
      val expectedIndexQueueStatus = IndexQueueStatus(0, 0, 0)
      whenever(indexQueueService.getIndexQueueStatus()).thenReturn(expectedIndexQueueStatus)
      whenever(indexBuildProperties.completeThreshold).thenReturn(100)
      whenever(prisonerRepository.count(GREEN)).thenReturn(99)
      whenever(prisonerRepository.count(BLUE)).thenReturn(100)

      maintainIndexService.markIndexingComplete(ignoreThreshold = false)

      verify(indexStatusService).markBuildCompleteAndSwitchIndex()
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

      assertThatThrownBy { maintainIndexService.markIndexingComplete(ignoreThreshold = false) }
        .isInstanceOf(ThresholdNotReachedException::class.java)
        .hasMessageContaining("The index prisoner-search-blue has not reached threshold 1000000 so")

      verify(indexStatusService).getIndexStatus()
      verify(indexQueueService).getIndexQueueStatus()
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
      assertThat(result).isEqualTo(IndexStatus(currentIndex = BLUE, currentIndexState = COMPLETED))
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
      assertThat(result).isEqualTo(IndexStatus(currentIndex = BLUE, currentIndexState = COMPLETED))
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

      assertThatThrownBy { maintainIndexService.switchIndex(false) }
        .isInstanceOf(BuildInProgressException::class.java)
        .hasMessageContaining("The build for BLUE is already BUILDING")

      verify(indexStatusService).getIndexStatus()
    }

    @Test
    fun `Index Cancelled returns error`() {
      val expectedIndexStatus = IndexStatus(currentIndex = GREEN, otherIndexState = CANCELLED)
      whenever(indexStatusService.getIndexStatus()).thenReturn(expectedIndexStatus)

      assertThatThrownBy { maintainIndexService.switchIndex(false) }
        .isInstanceOf(BuildCancelledException::class.java)
        .hasMessageContaining("The build for BLUE is in state CANCELLED")

      verify(indexStatusService).getIndexStatus()
    }

    @Test
    fun `Index Absent returns error`() {
      val expectedIndexStatus = IndexStatus(currentIndex = GREEN, otherIndexState = ABSENT)
      whenever(indexStatusService.getIndexStatus()).thenReturn(expectedIndexStatus)

      assertThatThrownBy { maintainIndexService.switchIndex(false) }
        .isInstanceOf(BuildAbsentException::class.java)
        .hasMessageContaining("The build for BLUE is in state ABSENT")

      verify(indexStatusService).getIndexStatus()
    }
  }

  @Nested
  inner class CancelIndexing {

    @Test
    fun `Index not building returns error`() {
      val expectedIndexStatus = IndexStatus(currentIndex = GREEN, otherIndexState = COMPLETED)
      whenever(indexStatusService.getIndexStatus()).thenReturn(expectedIndexStatus)

      assertThatThrownBy { maintainIndexService.cancelIndexing() }
        .isInstanceOf(BuildNotInProgressException::class.java)
        .hasMessageContaining("The index BLUE is in state COMPLETED")

      verify(indexStatusService).getIndexStatus()
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
      assertThat(result).isEqualTo(expectedIndexStatus)
    }
  }

  @Nested
  inner class IndexOffender {
    @Test
    internal fun `will delegate to synchronisation service if prisoner found in NOMIS`() {
      whenever(prisonerSynchroniserService.reindex(any(), any())).thenReturn(Prisoner())
      val indexStatus = IndexStatus(currentIndex = GREEN, currentIndexState = COMPLETED, otherIndexState = ABSENT)
      whenever(indexStatusService.getIndexStatus()).thenReturn(indexStatus)
      val booking = OffenderBookingBuilder().anOffenderBooking()
      whenever(nomisService.getOffender(any())).thenReturn(booking)

      maintainIndexService.indexPrisoner("ABC123D")

      verify(prisonerSynchroniserService).reindex(booking, listOf(GREEN))
    }

    @Test
    internal fun `will delete from index if prisoner only found in indices`() {
      val indexStatus = IndexStatus(currentIndex = GREEN, currentIndexState = COMPLETED, otherIndexState = ABSENT)
      whenever(indexStatusService.getIndexStatus()).thenReturn(indexStatus)
      whenever(prisonerRepository.get(any(), any())).thenReturn(Prisoner())

      maintainIndexService.indexPrisoner("ABC123D")

      verify(prisonerSynchroniserService).delete("ABC123D")
    }

    @Test
    internal fun `will raise a telemetry event if prisoner not found in NOMIS or indices`() {
      val indexStatus = IndexStatus(currentIndex = GREEN, currentIndexState = COMPLETED, otherIndexState = ABSENT)
      whenever(indexStatusService.getIndexStatus()).thenReturn(indexStatus)

      assertThatThrownBy { maintainIndexService.indexPrisoner("ABC123D") }
        .isInstanceOf(PrisonerNotFoundException::class.java)

      verify(telemetryClient).trackEvent(TelemetryEvents.PRISONER_NOT_FOUND.name, mapOf("prisonerNumber" to "ABC123D"), null)
    }

    @Test
    internal fun `will return the not found if prisoner not found in NOMIS or indices`() {
      val indexStatus = IndexStatus(currentIndex = GREEN, currentIndexState = COMPLETED, otherIndexState = ABSENT)
      whenever(indexStatusService.getIndexStatus()).thenReturn(indexStatus)

      assertThatThrownBy { maintainIndexService.indexPrisoner("ABC123D") }
        .isInstanceOf(PrisonerNotFoundException::class.java)
        .hasMessageContaining("The prisoner ABC123D")
    }

    @Test
    fun `No active indexes, update is not requested`() {
      whenever(indexStatusService.getIndexStatus()).thenReturn(IndexStatus.newIndex())

      assertThatThrownBy { maintainIndexService.indexPrisoner("SOME_CRN") }
        .isInstanceOf(NoActiveIndexesException::class.java)

      verifyNoInteractions(prisonerSynchroniserService)
    }

    @Test
    fun `No active indexes, error is returned`() {
      val indexStatus = IndexStatus.newIndex()
      whenever(indexStatusService.getIndexStatus()).thenReturn(indexStatus)

      assertThatThrownBy { maintainIndexService.indexPrisoner("SOME_CRN") }
        .isInstanceOf(NoActiveIndexesException::class.java)
        .hasMessageContaining("Cannot update current index NONE")
    }

    @Test
    fun `Current index active, offender is updated`() {
      val indexStatus = IndexStatus(currentIndex = GREEN, currentIndexState = COMPLETED)
      whenever(indexStatusService.getIndexStatus()).thenReturn(indexStatus)
      val booking = OffenderBookingBuilder().anOffenderBooking()
      whenever(nomisService.getOffender(any())).thenReturn(booking)

      assertThatThrownBy { maintainIndexService.indexPrisoner("SOME_CRN") }
        .isInstanceOf(PrisonerNotFoundException::class.java)

      verify(prisonerSynchroniserService).reindex(booking, listOf(indexStatus.currentIndex))
    }

    @Test
    fun `Other index active, offender is updated`() {
      val indexStatus = IndexStatus(currentIndex = NONE, otherIndexState = BUILDING, currentIndexState = ABSENT)
      whenever(indexStatusService.getIndexStatus()).thenReturn(indexStatus)
      val booking = OffenderBookingBuilder().anOffenderBooking()
      whenever(nomisService.getOffender(any())).thenReturn(booking)

      assertThatThrownBy { maintainIndexService.indexPrisoner("SOME_CRN") }
        .isInstanceOf(PrisonerNotFoundException::class.java)

      verify(prisonerSynchroniserService).reindex(booking, listOf(indexStatus.otherIndex))
    }

    @Test
    fun `Both indexes active, offender is updated on both indexes`() {
      val indexStatus = IndexStatus(currentIndex = GREEN, otherIndexState = BUILDING, currentIndexState = COMPLETED)
      whenever(indexStatusService.getIndexStatus()).thenReturn(indexStatus)
      val booking = OffenderBookingBuilder().anOffenderBooking()
      whenever(nomisService.getOffender(any())).thenReturn(booking)

      assertThatThrownBy { maintainIndexService.indexPrisoner("SOME_CRN") }
        .isInstanceOf(PrisonerNotFoundException::class.java)

      verify(prisonerSynchroniserService).reindex(booking, listOf(GREEN, BLUE))
    }
  }
}
