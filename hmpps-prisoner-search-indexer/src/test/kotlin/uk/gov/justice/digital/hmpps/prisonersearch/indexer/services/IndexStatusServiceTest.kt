package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.opensearch.client.IndicesClient
import org.opensearch.client.RestHighLevelClient
import org.opensearch.client.indices.GetIndexRequest
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.INDEX_STATUS_ID
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IndexState
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IndexStatus
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.SyncIndex
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.repository.IndexStatusRepository
import java.util.Optional

class IndexStatusServiceTest {

  private val indexStatusRepository = mock<IndexStatusRepository>()
  private val elasticSearchClient = mock<RestHighLevelClient>()
  private val indexAwsSqsClient = mock<IndicesClient>()
  private val indexStatusService = IndexStatusService(indexStatusRepository)

  @Nested
  inner class InitialiseIndexWhenRequired {
    @Nested
    inner class IndexAlreadyExists {
      @BeforeEach
      internal fun setUp() {
        whenever(elasticSearchClient.indices()).thenReturn(indexAwsSqsClient)
        whenever(indexAwsSqsClient.exists(any<GetIndexRequest>(), any())).thenReturn(true)
      }

      @Test
      internal fun `will add initial status to index`() {
        indexStatusService.initialiseIndexWhenRequired()
        verify(indexStatusRepository).save(any())
      }
    }
  }

  @Nested
  inner class GetCurrentIndexStatus {

    @Test
    fun `An existing index status should be returned`() {
      val existingIndexStatus = IndexStatus(currentIndex = SyncIndex.BLUE, otherIndexState = IndexState.BUILDING)
      whenever(indexStatusRepository.findById(INDEX_STATUS_ID)).thenReturn(Optional.ofNullable(existingIndexStatus))

      val actualIndexStatus = indexStatusService.getIndexStatus()

      verify(indexStatusRepository).findById(INDEX_STATUS_ID)
      assertThat(actualIndexStatus).isEqualTo(existingIndexStatus)
    }
  }

  @Nested
  inner class MarkBuildInProgress {
    @BeforeEach
    internal fun setUp() {
      whenever(indexStatusRepository.save(any<IndexStatus>())).thenAnswer { it.getArgument(0) }
    }

    @Test
    fun `Already building index does nothing`() {
      val existingIndexInProgress = IndexStatus(currentIndex = SyncIndex.BLUE, otherIndexState = IndexState.BUILDING)
      whenever(indexStatusRepository.findById(INDEX_STATUS_ID)).thenReturn(Optional.ofNullable(existingIndexInProgress))

      indexStatusService.markBuildInProgress()

      verify(indexStatusRepository, never()).save(any())
    }

    @Test
    fun `Not currently building index saves status building`() {
      val existingIndexNotInProgress = IndexStatus(currentIndex = SyncIndex.GREEN, otherIndexState = IndexState.ABSENT)
      whenever(indexStatusRepository.findById(INDEX_STATUS_ID)).thenReturn(Optional.ofNullable(existingIndexNotInProgress))

      val newIndexStatus = indexStatusService.markBuildInProgress()

      verify(indexStatusRepository).save(
        check { savedIndexStatus ->
          assertThat(savedIndexStatus.otherIndex).isEqualTo(SyncIndex.BLUE)
          assertThat(savedIndexStatus.otherIndexStartBuildTime).isNotNull()
          assertThat(savedIndexStatus.otherIndexState).isEqualTo(IndexState.BUILDING)
        },
      )

      assertThat(newIndexStatus).isNotNull
    }
  }

  @Nested
  inner class MarkBuildAbsent {
    @BeforeEach
    internal fun setUp() {
      whenever(indexStatusRepository.save(any<IndexStatus>())).thenAnswer { it.getArgument(0) }
    }

    @Test
    fun `Already building index does nothing`() {
      val existingIndexInProgress = IndexStatus(currentIndex = SyncIndex.BLUE, otherIndexState = IndexState.BUILDING)
      whenever(indexStatusRepository.findById(INDEX_STATUS_ID)).thenReturn(Optional.ofNullable(existingIndexInProgress))

      indexStatusService.markBuildAbsent()

      verify(indexStatusRepository, never()).save(any())
    }

    @Test
    fun `Not currently absent index saves status building`() {
      val existingIndexNotInProgress = IndexStatus(currentIndex = SyncIndex.GREEN, otherIndexState = IndexState.CANCELLED)
      whenever(indexStatusRepository.findById(INDEX_STATUS_ID)).thenReturn(Optional.ofNullable(existingIndexNotInProgress))

      val newIndexStatus = indexStatusService.markBuildAbsent()

      verify(indexStatusRepository).save(
        check { savedIndexStatus ->
          assertThat(savedIndexStatus.otherIndex).isEqualTo(SyncIndex.BLUE)
          assertThat(savedIndexStatus.otherIndexStartBuildTime).isNull()
          assertThat(savedIndexStatus.otherIndexState).isEqualTo(IndexState.ABSENT)
        },
      )

      assertThat(newIndexStatus).isNotNull
    }
  }

  @Nested
  inner class MarkBuildComplete {
    @BeforeEach
    internal fun setUp() {
      whenever(indexStatusRepository.save(any<IndexStatus>())).thenAnswer { it.getArgument(0) }
      whenever(elasticSearchClient.indices()).thenReturn(indexAwsSqsClient)
    }

    @Test
    fun `Not currently building index does nothing`() {
      val existingIndexNotInProgress = IndexStatus(currentIndex = SyncIndex.BLUE, otherIndexState = IndexState.COMPLETED)
      whenever(indexStatusRepository.findById(INDEX_STATUS_ID)).thenReturn(Optional.ofNullable(existingIndexNotInProgress))

      verify(indexStatusRepository, never()).save(any())
    }

    @Test
    fun `Currently building index updates repository to completed`() {
      val existingIndexInProgress = IndexStatus(currentIndex = SyncIndex.BLUE, otherIndexState = IndexState.BUILDING)
      whenever(indexStatusRepository.findById(INDEX_STATUS_ID)).thenReturn(Optional.ofNullable(existingIndexInProgress))

      val newIndexStatus = indexStatusService.markBuildCompleteAndSwitchIndex()

      verify(indexStatusRepository).save(
        check { savedIndexStatus ->
          assertThat(savedIndexStatus.currentIndex).isEqualTo(SyncIndex.GREEN)
          assertThat(savedIndexStatus.currentIndex).isNotNull()
          assertThat(savedIndexStatus.currentIndexState).isEqualTo(IndexState.COMPLETED)
        },
      )

      assertThat(newIndexStatus).isNotNull
    }
  }

  @Nested
  inner class MarkBuildCancelled {
    @BeforeEach
    internal fun setUp() {
      whenever(indexStatusRepository.save(any<IndexStatus>())).thenAnswer { it.getArgument(0) }
    }

    @Test
    fun `Build not currently in progress does nothing`() {
      val existingIndexNotInProgress = IndexStatus(currentIndex = SyncIndex.BLUE, otherIndexState = IndexState.COMPLETED)
      whenever(indexStatusRepository.findById(INDEX_STATUS_ID)).thenReturn(Optional.ofNullable(existingIndexNotInProgress))

      verify(indexStatusRepository, never()).save(any())
    }

    @Test
    fun `Build currently in progress updates repository to cancelled`() {
      val existingIndexInProgress = IndexStatus(currentIndex = SyncIndex.BLUE, otherIndexState = IndexState.BUILDING)
      whenever(indexStatusRepository.findById(INDEX_STATUS_ID)).thenReturn(Optional.ofNullable(existingIndexInProgress))

      val newIndexStatus = indexStatusService.markBuildCancelled()

      verify(indexStatusRepository).save(
        check { savedIndexStatus ->
          assertThat(savedIndexStatus.otherIndex).isEqualTo(SyncIndex.GREEN)
          assertThat(savedIndexStatus.otherIndexEndBuildTime).isNotNull()
          assertThat(savedIndexStatus.otherIndexState).isEqualTo(IndexState.CANCELLED)
        },
      )

      assertThat(newIndexStatus).isNotNull
    }
  }
}
