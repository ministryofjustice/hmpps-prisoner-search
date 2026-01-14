package uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners

import io.awspring.cloud.sqs.annotation.SqsListener
import org.springframework.retry.policy.NeverRetryPolicy
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.publish

@Service
class DomainEventPublisherListener(
  private val jsonMapper: JsonMapper,
  private val hmppsQueueService: HmppsQueueService,
) : EventListener {
  private inline fun <reified T> fromJson(message: String?): T = jsonMapper.readValue(message, T::class.java)

  private val hmppsDomainTopic by lazy {
    hmppsQueueService.findByTopicId("hmppseventtopic") ?: throw IllegalStateException("hmppseventtopic not found")
  }

  @SqsListener("publish", factory = "hmppsQueueContainerFactoryProxy")
  fun publish(eventJson: String) {
    val event = fromJson<DomainEvent>(eventJson)

    hmppsDomainTopic.publish(
      eventType = event.eventType,
      event = event.body,
      retryPolicy = NeverRetryPolicy(),
    )
  }
}

data class DomainEvent(val eventType: String, val body: String)
