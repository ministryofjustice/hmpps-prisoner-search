package uk.gov.justice.digital.hmpps.prisonersearch.indexer.batch

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.event.ContextRefreshedEvent
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.batch.BatchType.ACTIVE_INDEX_REFRESH
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.batch.BatchType.COMPARE_INDEX_SIZE
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.batch.BatchType.FULL_INDEX_REFRESH
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.ActiveMessagesExistException
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.CompareIndexService
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.IndexQueueStatus
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.MaintainIndexService
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.PrisonerDifferencesService
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.RefreshIndexService

@ExtendWith(OutputCaptureExtension::class)
class BatchManagerTest {

  private val compareIndexService = mock<CompareIndexService>()
  private val maintainIndexService = mock<MaintainIndexService>()
  private val prisonerDifferencesService = mock<PrisonerDifferencesService>()
  private val refreshIndexService = mock<RefreshIndexService>()
  private val event = mock<ContextRefreshedEvent>()
  private val context = mock<ConfigurableApplicationContext>()

  @BeforeEach
  fun setUp() {
    whenever(event.applicationContext).thenReturn(context)
  }

  @Nested
  inner class CompareIndexSize {
    @Test
    fun `should call the compare index size service`() = runTest {
      val batchManager = batchManager(COMPARE_INDEX_SIZE)

      batchManager.onApplicationEvent(event)

      verify(compareIndexService).doIndexSizeCheck()
      verify(context).close()
    }

    @Test
    fun `should not ignore errors in the compare index size service`() = runTest {
      whenever(compareIndexService.doIndexSizeCheck()).thenThrow(RuntimeException("error"))
      val batchManager = batchManager(COMPARE_INDEX_SIZE)

      assertThrows<RuntimeException> {
        batchManager.onApplicationEvent(event)
      }

      verify(compareIndexService).doIndexSizeCheck()
      verify(context, never()).close()
    }
  }

  @Nested
  inner class FullRefreshIndex {

    @Test
    fun `should call the refresh index service and remove old differences`() = runTest {
      val batchManager = batchManager(FULL_INDEX_REFRESH)

      batchManager.onApplicationEvent(event)

      verify(refreshIndexService).startFullIndexRefresh(true)
      verify(prisonerDifferencesService).deleteOldData()
      verify(context).close()
    }

    @Test
    fun `should ignore expected errors in the refresh index service`() = runTest {
      whenever(refreshIndexService.startFullIndexRefresh(true))
        .thenThrow(ActiveMessagesExistException(IndexQueueStatus(0, 0, 1), "action"))
      val batchManager = batchManager(FULL_INDEX_REFRESH)

      assertDoesNotThrow {
        batchManager.onApplicationEvent(event)
      }

      verify(refreshIndexService).startFullIndexRefresh(true)
      verify(context).close()
    }

    @Test
    fun `should not ignore unexpected errors in the refresh index service`() = runTest {
      whenever(refreshIndexService.startFullIndexRefresh(true))
        .thenThrow(RuntimeException("error"))
      val batchManager = batchManager(FULL_INDEX_REFRESH)

      assertThrows<RuntimeException> {
        batchManager.onApplicationEvent(event)
      }

      verify(refreshIndexService).startFullIndexRefresh(true)
      verify(context, never()).close()
    }
  }

  @Nested
  inner class ActiveRefreshIndex {

    @Test
    fun `should call the refresh index service and remove old differences`() = runTest {
      val batchManager = batchManager(ACTIVE_INDEX_REFRESH)

      batchManager.onApplicationEvent(event)

      verify(refreshIndexService).startActiveIndexRefresh(true)
      verify(prisonerDifferencesService).deleteOldData()
      verify(context).close()
    }

    @Test
    fun `should ignore expected errors in the refresh index service`() = runTest {
      whenever(refreshIndexService.startActiveIndexRefresh(true))
        .thenThrow(ActiveMessagesExistException(IndexQueueStatus(0, 0, 1), "action"))
      val batchManager = batchManager(ACTIVE_INDEX_REFRESH)

      assertDoesNotThrow {
        batchManager.onApplicationEvent(event)
      }

      verify(refreshIndexService).startActiveIndexRefresh(true)
      verify(context).close()
    }

    @Test
    fun `should not ignore unexpected errors in the refresh index service`() = runTest {
      whenever(refreshIndexService.startActiveIndexRefresh(true))
        .thenThrow(RuntimeException("error"))
      val batchManager = batchManager(ACTIVE_INDEX_REFRESH)

      assertThrows<RuntimeException> {
        batchManager.onApplicationEvent(event)
      }

      verify(refreshIndexService).startActiveIndexRefresh(true)
      verify(context, never()).close()
    }
  }

  private fun batchManager(batchType: BatchType) = BatchManager(
    batchType,
    compareIndexService,
    maintainIndexService,
    prisonerDifferencesService,
    refreshIndexService,
  )
}
