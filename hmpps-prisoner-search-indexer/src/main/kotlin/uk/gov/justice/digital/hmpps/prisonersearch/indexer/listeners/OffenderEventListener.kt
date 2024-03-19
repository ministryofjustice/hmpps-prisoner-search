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
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.OffenderEventQueueService
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.OffenderEventQueueService.Companion.REPUBLISH_SUFFIX

@Service
class OffenderEventListener(
  private val objectMapper: ObjectMapper,
  private val indexListenerService: IndexListenerService,
  private val offenderEventQueueService: OffenderEventQueueService,
) {
  private companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    private val movementEvent = setOf("EXTERNAL_MOVEMENT_RECORD-INSERTED", "EXTERNAL_MOVEMENT-CHANGED")
    private val bookingEvent = setOf(
      "ALERT-INSERTED",
      "ALERT-UPDATED",
      "ASSESSMENT-CHANGED",
      "BED_ASSIGNMENT_HISTORY-INSERTED",
      "CONFIRMED_RELEASE_DATE-CHANGED",
      "COURT_SENTENCE-CHANGED",
      "IMPRISONMENT_STATUS-CHANGED",
      "OFFENDER_BOOKING-CHANGED",
      "OFFENDER_PROFILE_DETAILS-INSERTED",
      "OFFENDER_PROFILE_DETAILS-UPDATED",
      "SENTENCE_DATES-CHANGED",
      "SENTENCING-CHANGED",
    )
    private val offenderEvent = setOf(
      "ASSESSMENT-UPDATED_$REPUBLISH_SUFFIX",
      "KEY_DATE_ADJUSTMENT_UPSERTED",
      "KEY_DATE_ADJUSTMENT_DELETED",
      "OFFENDER-INSERTED",
      "OFFENDER-UPDATED",
      "OFFENDER_ALIAS-CHANGED",
      "OFFENDER_CHARGES-UPDATED",
      "OFFENDER_DETAILS-CHANGED",
      "OFFENDER_IDENTIFIER-UPDATED",
      "OFFENDER_PHYSICAL_DETAILS-CHANGED",
      "SENTENCE_ADJUSTMENT_UPSERTED",
      "SENTENCE_ADJUSTMENT_DELETED",
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
        "ASSESSMENT-UPDATED" -> offenderEventQueueService.republishMessageWithDelay(requestJson!!, eventType)
        in movementEvent -> indexListenerService.externalMovement(fromJson(message), eventType)
        in bookingEvent -> indexListenerService.offenderBookingChange(fromJson(message), eventType)
        "BOOKING_NUMBER-CHANGED" -> indexListenerService.offenderBookNumberChange(fromJson(message), eventType)
        in offenderEvent -> indexListenerService.offenderChange(fromJson(message), eventType)
        "OFFENDER-DELETED" -> indexListenerService.maybeDeleteOffender(fromJson(message), eventType)
        "OFFENDER_BOOKING-REASSIGNED" -> indexListenerService.offenderBookingReassigned(fromJson(message), eventType)

        else -> log.warn("We received a message of event type {} which I really wasn't expecting", eventType)
      }
    } catch (e: Exception) {
      log.error("processOffenderEvent() Unexpected error", e)
      throw e
    }
  }

  private inline fun <reified T> fromJson(message: String?): T = objectMapper.readValue(message, T::class.java)
}
