@file:Suppress("PropertyName")

package uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners

import com.fasterxml.jackson.databind.ObjectMapper
import io.awspring.cloud.sqs.annotation.SqsListener
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.annotations.WithSpan
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.IndexListenerService

@Service
class OffenderEventListener(
  private val objectMapper: ObjectMapper,
  private val indexListenerService: IndexListenerService,
) {
  private companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    private val movementEvent = setOf("EXTERNAL_MOVEMENT_RECORD-INSERTED", "EXTERNAL_MOVEMENT-CHANGED")
    private val bookingEvent = setOf(
      "OFFENDER_BOOKING-CHANGED",
      "OFFENDER_BOOKING-REASSIGNED",
      "IMPRISONMENT_STATUS-CHANGED",
      "BED_ASSIGNMENT_HISTORY-INSERTED",
      "SENTENCE_DATES-CHANGED",
      "CONFIRMED_RELEASE_DATE-CHANGED",
      "ASSESSMENT-CHANGED",
      "OFFENDER_PROFILE_DETAILS-INSERTED",
      "OFFENDER_PROFILE_DETAILS-UPDATED",
      "SENTENCING-CHANGED",
      "ALERT-INSERTED",
      "ALERT-UPDATED",
    )
    private val offenderEvent = setOf(
      "OFFENDER-INSERTED",
      "OFFENDER-UPDATED",
      "OFFENDER_DETAILS-CHANGED",
      "OFFENDER_ALIAS-CHANGED",
      "OFFENDER_PHYSICAL_DETAILS-CHANGED",
      "OFFENDER_IDENTIFIER-UPDATED",
      "ASSESSMENT-UPDATED",
    )
  }

  @SqsListener("offenderqueue", factory = "hmppsQueueContainerFactoryProxy")
  @WithSpan(value = "syscon-devs-hmpps_prisoner_search_offender_queue", kind = SpanKind.SERVER)
  fun processOffenderEvent(requestJson: String?) {
    try {
      val (message, messageId, messageAttributes) = fromJson<Message>(requestJson)
      val eventType = messageAttributes.eventType.Value
      log.debug("Received message {} type {}", messageId, eventType)

      when (eventType) {
        in movementEvent -> indexListenerService.externalMovement(fromJson(message))
        in bookingEvent -> indexListenerService.offenderBookingChange(fromJson(message))
        "BOOKING_NUMBER-CHANGED" -> indexListenerService.offenderBookNumberChange(fromJson(message))
        in offenderEvent -> indexListenerService.offenderChange(fromJson(message))
        "OFFENDER-DELETED" -> indexListenerService.maybeDeleteOffender(fromJson(message))

        else -> log.warn("We received a message of event type {} which I really wasn't expecting", eventType)
      }
    } catch (e: Exception) {
      log.error("processOffenderEvent() Unexpected error", e)
      throw e
    }
  }

  private inline fun <reified T> fromJson(message: String?): T = objectMapper.readValue(message, T::class.java)
}
