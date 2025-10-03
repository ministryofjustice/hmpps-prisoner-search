package uk.gov.justice.digital.hmpps.prisonersearch.indexer.batch

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.event.ContextRefreshedEvent
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.CompareIndexService
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.MaintainIndexService
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.PrisonerDifferencesService
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.RefreshIndexService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchManager
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.CHECK_INDEX_COMPLETE
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.COMPARE_INDEX_SIZE
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.FULL_INDEX_REFRESH
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.REMOVE_OLD_DIFFERENCES

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
  fun `should call the compare index size service`() = runTest {
    val batchManager = batchManager(COMPARE_INDEX_SIZE)

    batchManager.onApplicationEvent(event)

    verify(compareIndexService).doIndexSizeCheck()
    verify(context).close()
  }

  @Test
  fun `should call the refresh index service`() = runTest {
    val batchManager = batchManager(FULL_INDEX_REFRESH)

    batchManager.onApplicationEvent(event)

    verify(refreshIndexService).startIndexRefresh()
    verify(context).close()
  }

  @Test
  fun `should call the remove old indexes service`() = runTest {
    val batchManager = batchManager(REMOVE_OLD_DIFFERENCES)

    batchManager.onApplicationEvent(event)

    verify(prisonerDifferencesService).deleteOldData()
    verify(context).close()
  }

  private fun batchManager(batchType: BatchType) = BatchManager(
    batchType,
    compareIndexService,
    maintainIndexService,
    prisonerDifferencesService,
    refreshIndexService,
  )
}
