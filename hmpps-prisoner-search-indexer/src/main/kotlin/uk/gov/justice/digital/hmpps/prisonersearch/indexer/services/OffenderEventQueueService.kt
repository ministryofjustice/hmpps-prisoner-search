package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.MissingQueueException

@Service
class OffenderEventQueueService(
  private val hmppsQueueService: HmppsQueueService,
  @Value("\${republish.delayInSeconds}") private val republishDelayInSeconds: Int,
) {
  private val offenderEventQueue by lazy {
    hmppsQueueService.findByQueueId("offenderqueue")
      ?: throw MissingQueueException("HmppsQueue offenderqueue not found")
  }
  private val offenderEventSqsClient by lazy { offenderEventQueue.sqsClient }
  private val offenderEventQueueUrl by lazy { offenderEventQueue.queueUrl }

  fun republishMessageWithDelay(message: String, eventType: String) {
    offenderEventSqsClient.sendMessage(
      SendMessageRequest.builder().queueUrl(offenderEventQueueUrl)
        // replace the event type in the raw JSON message. If we parsed the message first then we can't guarantee that
        // we've converted all the fields in the original JSON as the parser ignores unknown fields.
        .messageBody(message.replace(eventType, "${eventType}_$REPUBLISH_SUFFIX"))
        .delaySeconds(republishDelayInSeconds)
        .build(),
    ).get().also {
      log.info("Republished message of type {} with delay {} with id {}", eventType, republishDelayInSeconds, it.messageId())
    }
  }

  companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
    const val REPUBLISH_SUFFIX = "REPUBLISHED"
  }
}
