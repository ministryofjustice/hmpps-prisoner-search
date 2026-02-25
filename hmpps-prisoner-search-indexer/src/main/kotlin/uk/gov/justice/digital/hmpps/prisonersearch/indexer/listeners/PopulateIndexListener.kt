package uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners

import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners.IndexRequestType.POPULATE_INDEX
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners.IndexRequestType.POPULATE_PRISONER
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners.IndexRequestType.POPULATE_PRISONER_PAGE
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners.IndexRequestType.REFRESH_ACTIVE_INDEX
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners.IndexRequestType.REFRESH_ACTIVE_PRISONER_PAGE
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners.IndexRequestType.REFRESH_INDEX
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners.IndexRequestType.REFRESH_PRISONER
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners.IndexRequestType.REFRESH_PRISONER_PAGE
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.IndexException
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.PopulateIndexService
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.PrisonerPage
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.RefreshIndexService
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.RootOffenderIdPage
import java.lang.IllegalArgumentException

@Service
class PopulateIndexListener(
  private val jsonMapper: JsonMapper,
  private val populateIndexService: PopulateIndexService,
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
        POPULATE_INDEX -> populateIndexService.populateIndex()
        POPULATE_PRISONER_PAGE -> populateIndexService.populateIndexWithPrisonerPage(indexRequest.prisonerPage!!)
        POPULATE_PRISONER -> populateIndexService.populateIndexWithPrisoner(indexRequest.prisonerNumber!!)
        REFRESH_INDEX -> refreshIndexService.refreshIndex()
        REFRESH_PRISONER_PAGE -> refreshIndexService.refreshIndexWithPrisonerPage(indexRequest.prisonerPage!!)
        REFRESH_PRISONER -> refreshIndexService.refreshPrisoner(prisonerNumber = indexRequest.prisonerNumber!!)
        REFRESH_ACTIVE_INDEX -> refreshIndexService.refreshActiveIndex()
        REFRESH_ACTIVE_PRISONER_PAGE -> refreshIndexService.refreshActiveIndexWithRootOffenderIdPage(indexRequest.rootOffenderIdPage!!)
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
