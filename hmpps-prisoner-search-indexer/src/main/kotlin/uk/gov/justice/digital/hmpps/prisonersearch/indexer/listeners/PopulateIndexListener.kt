package uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners

import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners.IndexRequestType.REFRESH_ACTIVE_INDEX
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners.IndexRequestType.REFRESH_ACTIVE_PRISONER_PAGE
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners.IndexRequestType.REFRESH_INDEX
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners.IndexRequestType.REFRESH_PRISONER
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners.IndexRequestType.REFRESH_PRISONER_PAGE
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.IndexException
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.PrisonerPage
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.RefreshIndexService
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.RootOffenderIdPage

@Service
class PopulateIndexListener(
  private val jsonMapper: JsonMapper,
  private val refreshIndexService: RefreshIndexService,
) : EventListener {
  @SqsListener("index", factory = "hmppsQueueContainerFactoryProxy")
  fun processIndexRequest(requestJson: String) {
    val indexRequest: IndexMessageRequest = try {
      jsonMapper.readValue(requestJson)
    } catch (e: Exception) {
      log.error("Failed to process message {}", requestJson, e)
      throw e
    }
    try {
      when (indexRequest.type) {
        REFRESH_INDEX -> refreshIndexService.refreshIndex(indexRequest.domainEvents)
        REFRESH_PRISONER_PAGE -> refreshIndexService.refreshIndexWithRootOffenderIdPage(indexRequest.rootOffenderIdPage!!, indexRequest.domainEvents)
        REFRESH_PRISONER -> refreshIndexService.refreshPrisoner(prisonerNumber = indexRequest.prisonerNumber!!, indexRequest.domainEvents)
        REFRESH_ACTIVE_INDEX -> refreshIndexService.refreshActiveIndex(indexRequest.domainEvents)
        REFRESH_ACTIVE_PRISONER_PAGE -> refreshIndexService.refreshActiveIndexWithRootOffenderIdPage(indexRequest.rootOffenderIdPage!!, indexRequest.domainEvents)
        else -> {
          "Unknown request type for message $requestJson"
            .let {
              log.error(it)
              throw IllegalArgumentException(it)
            }
        }
      }
    } catch (e: IndexException) {
      log.error("Message {} failed with error {}", indexRequest, e.message)
    }
  }

  private companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}

data class IndexMessageRequest(
  val type: IndexRequestType?,
  val prisonerPage: PrisonerPage? = null,
  val rootOffenderIdPage: RootOffenderIdPage? = null,
  val prisonerNumber: String? = null,
  val rootOffenderId: Long? = null,
  val domainEvents: Boolean = false,
)

enum class IndexRequestType {
  POPULATE_INDEX,
  POPULATE_PRISONER_PAGE,
  POPULATE_PRISONER,
  REFRESH_INDEX,
  REFRESH_PRISONER_PAGE,
  REFRESH_PRISONER,
  REFRESH_ACTIVE_INDEX,
  REFRESH_ACTIVE_PRISONER_PAGE,
}
