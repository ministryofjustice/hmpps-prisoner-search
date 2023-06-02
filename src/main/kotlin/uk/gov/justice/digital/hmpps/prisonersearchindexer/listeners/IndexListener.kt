package uk.gov.justice.digital.hmpps.prisonersearchindexer.listeners

import arrow.core.getOrElse
import com.google.gson.Gson
import io.awspring.cloud.sqs.annotation.SqsListener
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
  private val gson: Gson,
  private val indexService: IndexService,
) {
  private companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener("index", factory = "hmppsQueueContainerFactoryProxy")
  fun processIndexRequest(requestJson: String) {
    val indexRequest = try {
      gson.fromJson(requestJson, IndexMessageRequest::class.java)
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
  val index: SyncIndex? = null,
  val prisonerPage: PrisonerPage? = null,
  val prisonerNumber: String? = null,
)

enum class IndexRequestType {
  POPULATE_INDEX, POPULATE_PRISONER_PAGE, POPULATE_PRISONER
}
