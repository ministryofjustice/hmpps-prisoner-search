@file:Suppress("PropertyName")

package uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import io.awspring.cloud.sqs.annotation.SqsListener
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.annotations.WithSpan
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.IndexListenerService

@Service
class DomainEventListener(
  private val objectMapper: ObjectMapper,
  private val indexListenerService: IndexListenerService,
) {
  private companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    private val incentiveEvent = setOf(
      "incentives.iep-review.inserted",
      "incentives.iep-review.updated",
      "incentives.iep-review.deleted",
      "incentives.prisoner.next-review-date-changed",
    )
    private val restrictedPatientEvent = setOf(
      "restricted-patients.patient.added",
      "restricted-patients.patient.removed",
      "restricted-patients.patient.supporting-prison-changed",
    )
  }

  @SqsListener("hmppsdomainqueue", factory = "hmppsQueueContainerFactoryProxy")
  @WithSpan(value = "syscon-devs-hmpps_prisoner_search_domain_queue", kind = SpanKind.SERVER)
  fun processDomainEvent(requestJson: String?) {
    try {
      val (message, messageId, messageAttributes) = fromJson<Message>(requestJson)
      val eventType = messageAttributes.eventType.Value
      log.debug("Received message {} type {}", messageId, eventType)

      when (eventType) {
        in incentiveEvent -> indexListenerService.incentiveChange(fromJson(message), eventType)
        in restrictedPatientEvent -> indexListenerService.restrictedPatientChange(fromJson(message), eventType)

        else -> log.warn("We received a message of event type {} which I really wasn't expecting", eventType)
      }
    } catch (e: Exception) {
      log.error("processDomainEvent() Unexpected error", e)
      throw e
    }
  }

  private inline fun <reified T> fromJson(message: String?): T = objectMapper.readValue(message, T::class.java)
}

data class IncentiveChangedMessage(
  val additionalInformation: IncentiveChangeAdditionalInformation,
  val eventType: String,
  val description: String,
)

data class IncentiveChangeAdditionalInformation(
  val nomsNumber: String,
  val id: Long,
)

data class RestrictedPatientMessage(
  val additionalInformation: RestrictedPatientAdditionalInformation,
  val eventType: String,
  val description: String,
)

data class RestrictedPatientAdditionalInformation(
  val prisonerNumber: String,
)

data class EventType(@JsonProperty("Value") val Value: String)
data class MessageAttributes(val eventType: EventType)
data class Message(val Message: String, val MessageId: String, val MessageAttributes: MessageAttributes)
