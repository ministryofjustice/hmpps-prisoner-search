package uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners

import com.fasterxml.jackson.databind.ObjectMapper
import io.awspring.cloud.sqs.annotation.SqsListener
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.annotations.WithSpan
import org.springframework.retry.policy.NeverRetryPolicy
import org.springframework.stereotype.Service
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.publish

@Service
class DomainEventPublisherListener(
  private val objectMapper: ObjectMapper,
  private val hmppsQueueService: HmppsQueueService,
) {
  private inline fun <reified T> fromJson(message: String?): T = objectMapper.readValue(message, T::class.java)

  private val hmppsDomainTopic by lazy {
    hmppsQueueService.findByTopicId("hmppseventtopic") ?: throw IllegalStateException("hmppseventtopic not found")
  }

  @SqsListener("publish", factory = "hmppsQueueContainerFactoryProxy")
  @WithSpan(value = "syscon-devs-hmpps_prisoner_search_publish_queue", kind = SpanKind.SERVER)
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
