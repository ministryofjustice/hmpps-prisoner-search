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
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.INDEX_STATUS_ID
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IndexState
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IndexStatus
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.repository.IndexStatusRepository
import java.util.Optional

class IndexStatusServiceTest {

  private val indexStatusRepository = mock<IndexStatusRepository>()
  private val elasticSearchClient = mock<RestHighLevelClient>()
  private val indexAwsSqsClient = mock<IndicesClient>()
  private val indexStatusService = IndexStatusService(indexStatusRepository)

  @Nested
  inner class GetCurrentIndexStatus {

    @Test
    fun `An existing index status should be returned`() {
      val existingIndexStatus = IndexStatus(currentIndexState = IndexState.BUILDING)
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
      val existingIndexInProgress = IndexStatus(currentIndexState = IndexState.BUILDING)
      whenever(indexStatusRepository.findById(INDEX_STATUS_ID)).thenReturn(Optional.ofNullable(existingIndexInProgress))

      indexStatusService.markBuildInProgress()

      verify(indexStatusRepository, never()).save(any())
    }

    @Test
    fun `Not currently building index saves status building`() {
      val existingIndexNotInProgress = IndexStatus(currentIndexState = IndexState.COMPLETED)
      whenever(indexStatusRepository.findById(INDEX_STATUS_ID)).thenReturn(Optional.ofNullable(existingIndexNotInProgress))

      val newIndexStatus = indexStatusService.markBuildInProgress()

      verify(indexStatusRepository).save(
        check { savedIndexStatus ->
          assertThat(savedIndexStatus.currentIndexStartBuildTime).isNotNull()
          assertThat(savedIndexStatus.currentIndexState).isEqualTo(IndexState.BUILDING)
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
      val existingIndexNotInProgress = IndexStatus(currentIndexState = IndexState.COMPLETED)
      whenever(indexStatusRepository.findById(INDEX_STATUS_ID)).thenReturn(Optional.ofNullable(existingIndexNotInProgress))

      verify(indexStatusRepository, never()).save(any())
    }

    @Test
    fun `Currently building index updates repository to completed`() {
      val existingIndexInProgress = IndexStatus(currentIndexState = IndexState.BUILDING)
      whenever(indexStatusRepository.findById(INDEX_STATUS_ID)).thenReturn(Optional.ofNullable(existingIndexInProgress))

      val newIndexStatus = indexStatusService.markBuildComplete()

      verify(indexStatusRepository).save(
        check { savedIndexStatus ->
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
      val existingIndexNotInProgress = IndexStatus(currentIndexState = IndexState.COMPLETED)
      whenever(indexStatusRepository.findById(INDEX_STATUS_ID)).thenReturn(Optional.ofNullable(existingIndexNotInProgress))

      verify(indexStatusRepository, never()).save(any())
    }

    @Test
    fun `Build currently in progress updates repository to cancelled`() {
      val existingIndexInProgress = IndexStatus(currentIndexState = IndexState.BUILDING)
      whenever(indexStatusRepository.findById(INDEX_STATUS_ID)).thenReturn(Optional.ofNullable(existingIndexInProgress))

      val newIndexStatus = indexStatusService.markBuildCancelled()

      verify(indexStatusRepository).save(
        check { savedIndexStatus ->
          assertThat(savedIndexStatus.currentIndexEndBuildTime).isNotNull()
          assertThat(savedIndexStatus.currentIndexState).isEqualTo(IndexState.CANCELLED)
        },
      )

      assertThat(newIndexStatus).isNotNull
    }
  }
}
