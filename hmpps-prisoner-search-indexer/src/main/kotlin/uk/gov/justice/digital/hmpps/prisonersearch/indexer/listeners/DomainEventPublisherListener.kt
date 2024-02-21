package uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners

import com.fasterxml.jackson.databind.ObjectMapper
import io.awspring.cloud.sqs.annotation.SqsListener
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.annotations.WithSpan
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.hmpps.sqs.HmppsQueueService

@Service
class DomainEventPublisherListener(
  private val objectMapper: ObjectMapper,
  private val hmppsQueueService: HmppsQueueService,
) {
  private inline fun <reified T> fromJson(message: String?): T = objectMapper.readValue(message, T::class.java)

  private val hmppsDomainTopic by lazy {
    hmppsQueueService.findByTopicId("hmppseventtopic") ?: throw IllegalStateException("hmppseventtopic not found")
  }
  private val topicArn by lazy { hmppsDomainTopic.arn }
  private val topicSnsClient by lazy { hmppsDomainTopic.snsClient }

  @SqsListener("publish", factory = "hmppsQueueContainerFactoryProxy")
  @WithSpan(value = "syscon-devs-hmpps_prisoner_search_publish_queue", kind = SpanKind.SERVER)
  fun publish(eventJson: String) {
    val event = fromJson<DomainEvent>(eventJson)

    val request = PublishRequest.builder()
      .topicArn(topicArn)
      .message(event.body)
      .messageAttributes(
        mapOf(
          "eventType" to MessageAttributeValue.builder().dataType("String").stringValue(event.eventType).build(),
        ),
      ).build()

    topicSnsClient.publish(request).join()
  }
}

data class DomainEvent(val eventType: String, val body: String)
