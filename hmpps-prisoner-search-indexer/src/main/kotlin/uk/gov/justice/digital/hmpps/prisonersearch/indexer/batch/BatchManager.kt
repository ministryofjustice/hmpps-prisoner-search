package uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch

import io.opentelemetry.instrumentation.annotations.SpanAttribute
import io.opentelemetry.instrumentation.annotations.WithSpan
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.CompareIndexService
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.MaintainIndexService
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.PrisonerDifferencesService
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.RefreshIndexService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.CHECK_INDEX_COMPLETE
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.COMPARE_INDEX_SIZE
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.FULL_INDEX_REFRESH
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.batch.BatchType.REMOVE_OLD_DIFFERENCES

enum class BatchType {
  CHECK_INDEX_COMPLETE,
  COMPARE_INDEX_SIZE,
  FULL_INDEX_REFRESH,
  REMOVE_OLD_DIFFERENCES,
}

@ConditionalOnProperty(name = ["batch.enabled"], havingValue = "true")
@Service
class BatchManager(
  @Value($$"${batch.type}") private val batchType: BatchType,
  private val compareIndexService: CompareIndexService,
  private val maintainIndexService: MaintainIndexService,
  private val prisonerDifferencesService: PrisonerDifferencesService,
  private val refreshIndexService: RefreshIndexService,
) {

  @EventListener
  fun onApplicationEvent(event: ContextRefreshedEvent) = runBatchJob(batchType).also { event.closeApplication() }

  @WithSpan
  fun runBatchJob(@SpanAttribute batchType: BatchType) = runBlocking {
    when (batchType) {
      CHECK_INDEX_COMPLETE -> maintainIndexService.markIndexingComplete()
      COMPARE_INDEX_SIZE -> compareIndexService.doIndexSizeCheck()
      FULL_INDEX_REFRESH -> refreshIndexService.startIndexRefresh()
      REMOVE_OLD_DIFFERENCES -> prisonerDifferencesService.deleteOldData()
    }
  }

  private fun ContextRefreshedEvent.closeApplication() = (this.applicationContext as ConfigurableApplicationContext).close()
}
