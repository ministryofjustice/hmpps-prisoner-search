@file:OptIn(ExperimentalCoroutinesApi::class)

package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IndexState.ABSENT
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IndexState.BUILDING
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IndexState.CANCELLED
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IndexState.COMPLETED
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IndexStatus
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.SyncIndex.GREEN

class RefreshIndexServiceTest {

  private val indexStatusService = mock<IndexStatusService>()
  private val indexQueueService = mock<IndexQueueService>()
  private val refreshIndexService = RefreshIndexService(indexStatusService, indexQueueService)

  @Nested
  inner class StartIndexRefresh {
    @Test
    fun `Index already building returns error`() {
      val expectedIndexStatus = IndexStatus(currentIndex = GREEN, otherIndexState = BUILDING)
      whenever(indexStatusService.getIndexStatus()).thenReturn(expectedIndexStatus)

      assertThatThrownBy { refreshIndexService.startIndexRefresh() }
        .isInstanceOf(BuildAlreadyInProgressException::class.java)
        .hasMessageContaining("The build for BLUE is already BUILDING")

      verify(indexStatusService).getIndexStatus()
    }

    @Test
    fun `No active indexes returns error`() {
      val expectedIndexStatus =
        IndexStatus(currentIndex = GREEN, currentIndexState = ABSENT, otherIndexState = CANCELLED)
      whenever(indexStatusService.getIndexStatus()).thenReturn(expectedIndexStatus)

      assertThatThrownBy { refreshIndexService.startIndexRefresh() }
        .isInstanceOf(BuildAbsentException::class.java)
        .hasMessageContaining("The build for BLUE is in state CANCELLED")

      verify(indexStatusService).getIndexStatus()
    }

    @Test
    fun `Index has active messages returns an error`() {
      whenever(indexStatusService.getIndexStatus()).thenReturn(
        IndexStatus(
          currentIndex = GREEN,
          otherIndexState = COMPLETED,
        ),
      )
      val expectedIndexQueueStatus = IndexQueueStatus(1, 0, 0)
      whenever(indexQueueService.getIndexQueueStatus()).thenReturn(expectedIndexQueueStatus)

      assertThatThrownBy { refreshIndexService.startIndexRefresh() }
        .isInstanceOf(ActiveMessagesExistException::class.java)
        .hasMessageContaining("The index prisoner-search-green has active messages")
    }

    @Test
    fun `A request is made to mark the index build is in progress`() {
      whenever(indexQueueService.getIndexQueueStatus()).thenReturn(IndexQueueStatus(0, 0, 0))

      whenever(indexStatusService.getIndexStatus())
        .thenReturn(IndexStatus(currentIndex = GREEN, currentIndexState = COMPLETED, otherIndexState = ABSENT))

      refreshIndexService.startIndexRefresh()

      verify(indexQueueService).sendRefreshIndexMessage(GREEN)
    }
  }
}
