package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners.Message
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.MissingQueueException
import uk.gov.justice.hmpps.sqs.eventTypeMessageAttributes

@Service
class OffenderEventQueueService(
  private val objectMapper: ObjectMapper,
  private val hmppsQueueService: HmppsQueueService,
  @Value("\${republish.delayInSeconds}") private val republishDelayInSeconds: Int,
) {
  private val offenderEventQueue by lazy {
    hmppsQueueService.findByQueueId("offenderqueue")
      ?: throw MissingQueueException("HmppsQueue offenderqueue not found")
  }
  private val offenderEventSqsClient by lazy { offenderEventQueue.sqsClient }
  private val offenderEventQueueUrl by lazy { offenderEventQueue.queueUrl }

  private val domainEventQueue by lazy {
    hmppsQueueService.findByQueueId("hmppsdomainqueue")
      ?: throw MissingQueueException("HmppsQueue hmppsdomainqueue not found")
  }
  private val domainEventSqsClient by lazy { domainEventQueue.sqsClient }
  private val domainEventQueueUrl by lazy { domainEventQueue.queueUrl }

  fun republishMessageWithDelay(message: String, eventType: String) {
    val republishEventType = "${eventType}_$REPUBLISH_SUFFIX"
    offenderEventSqsClient.sendMessage(
      SendMessageRequest.builder().queueUrl(offenderEventQueueUrl)
        // replace the event type in the raw JSON message. If we parsed the message first then we can't guarantee that
        // we've converted all the fields in the original JSON as the parser ignores unknown fields.
        .messageBody(message.replace(eventType, republishEventType))
        .eventTypeMessageAttributes(republishEventType)
        .delaySeconds(republishDelayInSeconds)
        .build(),
    ).get().also {
      log.info("Republished message of type {} with delay {} with id {}", eventType, republishDelayInSeconds, it.messageId())
    }
  }

  fun requeueMessageWithDelay(message: String, eventType: String, destination: RequeueDestination, delayInSeconds: Int = republishDelayInSeconds) {
    when (destination) {
      RequeueDestination.OFFENDER -> offenderEventSqsClient
      RequeueDestination.DOMAIN -> domainEventSqsClient
    }.sendMessage(
      SendMessageRequest.builder()
        .queueUrl(
          when (destination) {
            RequeueDestination.OFFENDER -> offenderEventQueueUrl
            RequeueDestination.DOMAIN -> domainEventQueueUrl
          },
        )
        .messageBody(message)
        .eventTypeMessageAttributes(eventType)
        .delaySeconds(delayInSeconds)
        .build(),
    ).get().also {
      log.info("Requeued message of type {} with delay {} with id {}", eventType, delayInSeconds, it.messageId())
    }
  }

  fun handleLockingFailure(olfe: OptimisticLockingFailureException, destination: RequeueDestination, requestJson: String?) {
    if (
      olfe.message?.contains("Cannot index a document due to seq_no+primary_term conflict") == true ||
      olfe.message?.contains("version conflict, document already exists") == true
    ) {
      // This is not an error, and so we want to avoid exceptions being logged
      val (message, _, messageAttributes) = objectMapper.readValue(requestJson, Message::class.java)
      log.info("Detected a seq_no+primary_term conflict and trying again for message:\n{}", message)
      requeueMessageWithDelay(
        requestJson!!,
        messageAttributes.eventType.Value,
        destination,
        delayInSeconds = 1,
      )
    } else {
      log.error("Unexpected error", olfe)
      throw olfe
    }
  }

  companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
    const val REPUBLISH_SUFFIX = "REPUBLISHED"
  }

  enum class RequeueDestination {
    OFFENDER,
    DOMAIN,
  }
}
