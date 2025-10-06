package uk.gov.justice.digital.hmpps.prisonersearch.indexer.batch

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
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
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IndexStatus
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.batch.BatchType.CHECK_INDEX_COMPLETE
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.batch.BatchType.COMPARE_INDEX_SIZE
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.batch.BatchType.FULL_INDEX_REFRESH
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.batch.BatchType.REMOVE_OLD_DIFFERENCES
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.BuildAlreadyInProgressException
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.BuildNotInProgressException
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.CompareIndexService
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

  @Test
  fun `should call the check index complete service`() = runTest {
    val batchManager = batchManager(CHECK_INDEX_COMPLETE)

    batchManager.onApplicationEvent(event)

    verify(maintainIndexService).markIndexingComplete()
    verify(context).close()
  }

  @Test
  fun `should ignore expected errors in the check index complete service`() = runTest {
    whenever(maintainIndexService.markIndexingComplete())
      .thenThrow(BuildNotInProgressException(IndexStatus("some-id").toBuildInProgress()))
    val batchManager = batchManager(CHECK_INDEX_COMPLETE)

    assertDoesNotThrow {
      batchManager.onApplicationEvent(event)
    }

    verify(maintainIndexService).markIndexingComplete()
    verify(context).close()
  }

  @Test
  fun `should not ignore unexpected error in the check index complete service`() = runTest {
    whenever(maintainIndexService.markIndexingComplete()).thenThrow(RuntimeException("error"))
    val batchManager = batchManager(CHECK_INDEX_COMPLETE)

    assertThrows<RuntimeException> {
      batchManager.onApplicationEvent(event)
    }

    verify(maintainIndexService).markIndexingComplete()
    verify(context, never()).close()
  }

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

  @Test
  fun `should call the refresh index service`() = runTest {
    val batchManager = batchManager(FULL_INDEX_REFRESH)

    batchManager.onApplicationEvent(event)

    verify(refreshIndexService).startIndexRefresh()
    verify(context).close()
  }

  @Test
  fun `should ignore expected errors in the refresh index service`() = runTest {
    whenever(refreshIndexService.startIndexRefresh())
      .thenThrow(BuildAlreadyInProgressException(IndexStatus("some-id").toBuildInProgress()))
    val batchManager = batchManager(FULL_INDEX_REFRESH)

    assertDoesNotThrow {
      batchManager.onApplicationEvent(event)
    }

    verify(refreshIndexService).startIndexRefresh()
    verify(context).close()
  }

  @Test
  fun `should not ignore unexpected errors in the refresh index service`() = runTest {
    whenever(refreshIndexService.startIndexRefresh())
      .thenThrow(RuntimeException("error"))
    val batchManager = batchManager(FULL_INDEX_REFRESH)

    assertThrows<RuntimeException> {
      batchManager.onApplicationEvent(event)
    }

    verify(refreshIndexService).startIndexRefresh()
    verify(context, never()).close()
  }

  @Test
  fun `should call the remove old differences service`() = runTest {
    val batchManager = batchManager(REMOVE_OLD_DIFFERENCES)

    batchManager.onApplicationEvent(event)

    verify(prisonerDifferencesService).deleteOldData()
    verify(context).close()
  }

  @Test
  fun `should not ignore errors in the remove old differences service`() = runTest {
    whenever(prisonerDifferencesService.deleteOldData()).thenThrow(RuntimeException("error"))
    val batchManager = batchManager(REMOVE_OLD_DIFFERENCES)

    assertThrows<RuntimeException> {
      batchManager.onApplicationEvent(event)
    }

    verify(prisonerDifferencesService).deleteOldData()
    verify(context, never()).close()
  }

  private fun batchManager(batchType: BatchType) = BatchManager(
    batchType,
    compareIndexService,
    maintainIndexService,
    prisonerDifferencesService,
    refreshIndexService,
  )
}
