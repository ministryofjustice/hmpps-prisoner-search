package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest
import software.amazon.awssdk.services.sqs.model.QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES
import software.amazon.awssdk.services.sqs.model.QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import software.amazon.awssdk.services.sqs.model.SendMessageResponse
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners.IndexMessageRequest
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners.IndexRequestType
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners.IndexRequestType.POPULATE_PRISONER
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners.IndexRequestType.REFRESH_PRISONER
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue
import uk.gov.justice.hmpps.sqs.eventTypeMessageAttributes

data class IndexQueueStatus(val messagesOnQueue: Int, val messagesOnDlq: Int, val messagesInFlight: Int) {
  val active
    get() = messagesOnQueue > 0 || messagesOnDlq > 0 || messagesInFlight > 0
}

@Service
class IndexQueueService(
  private val hmppsQueueService: HmppsQueueService,
  private val jsonMapper: JsonMapper,
) {
  private val indexQueue by lazy { hmppsQueueService.findByQueueId("index") as HmppsQueue }
  private val indexSqsClient by lazy { indexQueue.sqsClient }
  private val indexSqsDlqClient by lazy { indexQueue.sqsDlqClient }
  private val indexQueueUrl by lazy { indexQueue.queueUrl }
  private val indexDlqUrl by lazy { indexQueue.dlqUrl as String }

  fun sendIndexMessage(type: IndexRequestType) {
    sendMessage(IndexMessageRequest(type = type)).also {
      log.info("Sent {} message request {}", type, it.messageId())
    }
  }

  private fun sendMessage(request: IndexMessageRequest, noTracing: Boolean = false): SendMessageResponse = indexSqsClient.sendMessage(
    SendMessageRequest.builder().queueUrl(indexQueueUrl)
      .messageBody(jsonMapper.writeValueAsString(request))
      .eventTypeMessageAttributes("hmpps-prisoner-search-indexer-${request.type?.name?.lowercase()}", noTracing = noTracing)
      .build(),
  ).get()

  fun sendPrisonerPageMessage(prisonerPage: PrisonerPage, type: IndexRequestType) {
    sendMessage(IndexMessageRequest(type = type, prisonerPage = prisonerPage)).also {
      log.info("Sent {} prisoner page message request {} for page {}", type, it.messageId(), prisonerPage)
    }
  }

  fun sendPopulatePrisonerMessage(prisonerNumber: String) = sendMessage(IndexMessageRequest(type = POPULATE_PRISONER, prisonerNumber = prisonerNumber), noTracing = true)

  fun sendRefreshPrisonerMessage(prisonerNumber: String) = sendMessage(IndexMessageRequest(type = REFRESH_PRISONER, prisonerNumber = prisonerNumber), noTracing = true)

  fun getNumberOfMessagesCurrentlyOnIndexQueue(): Int = indexSqsClient.countMessagesOnQueue(indexQueueUrl).get()

  fun getNumberOfMessagesCurrentlyOnIndexDLQ(): Int = indexSqsDlqClient?.countMessagesOnQueue(indexDlqUrl)?.get() ?: 0

  fun getIndexQueueStatus(): IndexQueueStatus {
    val queueAttributes = indexSqsClient.getQueueAttributes(
      GetQueueAttributesRequest.builder()
        .queueUrl(indexQueueUrl)
        .attributeNames(APPROXIMATE_NUMBER_OF_MESSAGES, APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE)
        .build(),
    ).get()

    return IndexQueueStatus(
      messagesOnQueue = queueAttributes.attributes()[APPROXIMATE_NUMBER_OF_MESSAGES]?.toInt() ?: 0,
      messagesInFlight = queueAttributes.attributes()[APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE]?.toInt() ?: 0,
      messagesOnDlq = getNumberOfMessagesCurrentlyOnIndexDLQ(),
    )
  }

  private companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
