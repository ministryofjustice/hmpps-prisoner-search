package uk.gov.justice.digital.hmpps.prisonersearch.indexer.batch

import io.opentelemetry.instrumentation.annotations.SpanAttribute
import io.opentelemetry.instrumentation.annotations.WithSpan
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.batch.BatchType.ACTIVE_INDEX_REFRESH
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.batch.BatchType.CHECK_INDEX_COMPLETE
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.batch.BatchType.COMPARE_INDEX_SIZE
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.batch.BatchType.FULL_INDEX_REFRESH
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.CompareIndexService
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.IndexException
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.MaintainIndexService
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.PrisonerDifferencesService
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.RefreshIndexService

enum class BatchType {
  CHECK_INDEX_COMPLETE,
  COMPARE_INDEX_SIZE,
  FULL_INDEX_REFRESH,
  ACTIVE_INDEX_REFRESH,
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
      CHECK_INDEX_COMPLETE -> ignoreFailure<IndexException> { maintainIndexService.markIndexingComplete() }
      COMPARE_INDEX_SIZE -> compareIndexService.doIndexSizeCheck()
      FULL_INDEX_REFRESH -> {
        prisonerDifferencesService.deleteOldData()
        ignoreFailure<IndexException> { refreshIndexService.startFullIndexRefresh() }
      }
      ACTIVE_INDEX_REFRESH -> {
        prisonerDifferencesService.deleteOldData()
        ignoreFailure<IndexException> { refreshIndexService.startActiveIndexRefresh() }
      }
    }
  }

  private fun ContextRefreshedEvent.closeApplication() = (this.applicationContext as ConfigurableApplicationContext).close()

  private inline fun <reified T> ignoreFailure(block: () -> Unit) = runCatching(block)
    .onFailure {
      if (it is T) {
        log.warn("Ignoring failure: ${it.message}")
      } else {
        throw it
      }
    }

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}
