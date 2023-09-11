package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest
import software.amazon.awssdk.services.sqs.model.QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES
import software.amazon.awssdk.services.sqs.model.QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import software.amazon.awssdk.services.sqs.model.SendMessageResponse
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.SyncIndex
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners.IndexMessageRequest
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners.IndexRequestType
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners.IndexRequestType.COMPARE_INDEX
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners.IndexRequestType.POPULATE_INDEX
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue

data class IndexQueueStatus(val messagesOnQueue: Int, val messagesOnDlq: Int, val messagesInFlight: Int) {
  val active
    get() = messagesOnQueue > 0 || messagesOnDlq > 0 || messagesInFlight > 0
}

@Service
class IndexQueueService(
  private val hmppsQueueService: HmppsQueueService,
  private val objectMapper: ObjectMapper,
) {
  private val indexQueue by lazy { hmppsQueueService.findByQueueId("index") as HmppsQueue }
  private val indexSqsClient by lazy { indexQueue.sqsClient }
  private val indexSqsDlqClient by lazy { indexQueue.sqsDlqClient }
  private val indexQueueUrl by lazy { indexQueue.queueUrl }
  private val indexDlqUrl by lazy { indexQueue.dlqUrl as String }

  fun sendIndexMessage(index: SyncIndex, type: IndexRequestType) {
    sendMessage(IndexMessageRequest(type = POPULATE_INDEX, index = index)).also {
      log.info("Sent populate index message request {}", it.messageId())
    }
  }

  fun sendRefreshIndexMessage(index: SyncIndex) {
    sendMessage(IndexMessageRequest(type = COMPARE_INDEX, index = index)).also {
      log.info("Sent refresh index message request {}", it.messageId())
    }
  }

  fun sendMessage(request: IndexMessageRequest): SendMessageResponse = indexSqsClient.sendMessage(
    SendMessageRequest.builder().queueUrl(indexQueueUrl)
      .messageBody(objectMapper.writeValueAsString(request))
      .build(),
  ).get()

  fun sendPrisonerPageMessage(prisonerPage: PrisonerPage, type: IndexRequestType) {
    sendMessage(IndexMessageRequest(type = type, prisonerPage = prisonerPage)).also {
      log.info("Sent {} prisoner page message request {} for page {}", type, it.messageId(), prisonerPage)
    }
  }

  fun sendPrisonerMessage(prisonerNumber: String, type: IndexRequestType) {
    indexSqsClient.sendMessage(
      SendMessageRequest.builder().queueUrl(indexQueueUrl).messageBody(
        objectMapper.writeValueAsString(
          IndexMessageRequest(type = type, prisonerNumber = prisonerNumber),
        ),
      ).build(),
    ).get()
  }

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
