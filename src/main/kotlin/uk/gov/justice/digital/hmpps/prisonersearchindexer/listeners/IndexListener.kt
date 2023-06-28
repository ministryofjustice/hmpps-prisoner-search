package uk.gov.justice.digital.hmpps.prisonersearchindexer.listeners

import arrow.core.getOrElse
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL
import com.fasterxml.jackson.databind.ObjectMapper
import io.awspring.cloud.sqs.annotation.SqsListener
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.annotations.WithSpan
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearchindexer.listeners.IndexRequestType.POPULATE_INDEX
import uk.gov.justice.digital.hmpps.prisonersearchindexer.listeners.IndexRequestType.POPULATE_PRISONER
import uk.gov.justice.digital.hmpps.prisonersearchindexer.listeners.IndexRequestType.POPULATE_PRISONER_PAGE
import uk.gov.justice.digital.hmpps.prisonersearchindexer.model.SyncIndex
import uk.gov.justice.digital.hmpps.prisonersearchindexer.services.IndexService
import uk.gov.justice.digital.hmpps.prisonersearchindexer.services.PrisonerPage
import java.lang.IllegalArgumentException

@Service
class IndexListener(
  private val objectMapper: ObjectMapper,
  private val indexService: IndexService,
) {
  private companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener("index", factory = "hmppsQueueContainerFactoryProxy")
  @WithSpan(value = "syscon-devs-hmpps_prisoner_search_index_queue", kind = SpanKind.SERVER)
  fun processIndexRequest(requestJson: String) {
    val indexRequest = try {
      objectMapper.readValue(requestJson, IndexMessageRequest::class.java)
    } catch (e: Exception) {
      log.error("Failed to process message {}", requestJson, e)
      throw e
    }
    when (indexRequest.type) {
      POPULATE_INDEX -> indexService.populateIndex(indexRequest.index!!)
      POPULATE_PRISONER_PAGE -> indexService.populateIndexWithPrisonerPage(indexRequest.prisonerPage!!)
      POPULATE_PRISONER -> indexService.populateIndexWithPrisoner(indexRequest.prisonerNumber!!)
      else -> {
        "Unknown request type for message $requestJson"
          .let {
            log.error(it)
            throw IllegalArgumentException(it)
          }
      }
    }
      .getOrElse { log.error("Message {} failed with error {}", indexRequest, it) }
  }
}

data class IndexMessageRequest(
  val type: IndexRequestType?,
  @JsonInclude(NON_NULL)
  val index: SyncIndex? = null,
  @JsonInclude(NON_NULL)
  val prisonerPage: PrisonerPage? = null,
  @JsonInclude(NON_NULL)
  val prisonerNumber: String? = null,
)

enum class IndexRequestType {
  POPULATE_INDEX, POPULATE_PRISONER_PAGE, POPULATE_PRISONER
}
