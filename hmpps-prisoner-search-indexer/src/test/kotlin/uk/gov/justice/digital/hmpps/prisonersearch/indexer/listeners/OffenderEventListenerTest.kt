package uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners

import ch.qos.logback.classic.Level
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.json.JsonTest
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.helpers.findLogAppender
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.ExternalPrisonerMovementMessage
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.IndexListenerService
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.OffenderBookingChangedMessage
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.OffenderBookingReassignedMessage
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.OffenderChangedMessage
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.OffenderEventQueueService

@JsonTest
internal class OffenderEventListenerTest(@Autowired private val objectMapper: ObjectMapper) {
  private val indexListenerService = mock<IndexListenerService>()
  private val offenderEventQueueService = mock<OffenderEventQueueService>()

  private val listener = OffenderEventListener(objectMapper, indexListenerService, offenderEventQueueService)

  private val logAppender = findLogAppender(OffenderEventListener::class.java)

  @Nested
  inner class ProcessOffenderEvent {
    @ParameterizedTest
    @ValueSource(strings = ["EXTERNAL_MOVEMENT_RECORD-INSERTED", "EXTERNAL_MOVEMENT-CHANGED"])
    internal fun `will call service for external movement`(eventType: String) {
      listener.processOffenderEvent(validExternalMovementMessage(eventType))
      verify(indexListenerService).externalMovement(ExternalPrisonerMovementMessage(bookingId = 12345L), eventType)
    }

    @ParameterizedTest
    @ValueSource(
      strings = [
        "OFFENDER_BOOKING-CHANGED", "IMPRISONMENT_STATUS-CHANGED",
        "BED_ASSIGNMENT_HISTORY-INSERTED", "SENTENCE_DATES-CHANGED", "CONFIRMED_RELEASE_DATE-CHANGED",
        "ASSESSMENT-CHANGED", "OFFENDER_PROFILE_DETAILS-INSERTED", "OFFENDER_PROFILE_DETAILS-UPDATED",
        "SENTENCING-CHANGED", "ALERT-INSERTED", "ALERT-UPDATED",
      ],
    )
    internal fun `will call service for booking change`(eventType: String) {
      listener.processOffenderEvent(validOffenderBookingChangedMessage(eventType))
      verify(indexListenerService).offenderBookingChange(OffenderBookingChangedMessage(bookingId = 4323342L), eventType)
    }

    @ParameterizedTest
    @ValueSource(strings = ["BOOKING_NUMBER-CHANGED"])
    internal fun `will call service for booking number change`(eventType: String) {
      listener.processOffenderEvent(validOffenderBookingChangedMessage(eventType))
      verify(indexListenerService).offenderBookNumberChange(OffenderBookingChangedMessage(bookingId = 4323342L), eventType)
    }

    @ParameterizedTest
    @ValueSource(
      strings = [
        "OFFENDER-INSERTED", "OFFENDER-UPDATED", "OFFENDER_DETAILS-CHANGED", "OFFENDER_ALIAS-CHANGED",
        "OFFENDER_PHYSICAL_DETAILS-CHANGED", "OFFENDER_IDENTIFIER-UPDATED", "ASSESSMENT-UPDATED_REPUBLISHED",
        "OFFENDER_ADDRESS-INSERTED", "OFFENDER_ADDRESS-UPDATED", "OFFENDER_EMAIL-INSERTED", "OFFENDER_EMAIL-DELETED",
        "OFFENDER_EMAIL-UPDATED", "OFFENDER_PHONE-INSERTED", "OFFENDER_PHONE-DELETED", "OFFENDER_PHONE-UPDATED",
        "OFFENDER_ADDRESS_PHONE-INSERTED", "OFFENDER_ADDRESS_PHONE-UPDATED", "OFFENDER_ADDRESS_PHONE-DELETED",
      ],
    )
    internal fun `will call service for offender change`(eventType: String) {
      listener.processOffenderEvent(validOffenderChangedMessage(eventType))
      verify(indexListenerService).offenderChange(
        OffenderChangedMessage(
          eventType = eventType,
          offenderIdDisplay = "A123ZZZ",
          offenderId = 2345612,
        ),
        eventType,
      )
    }

    @ParameterizedTest
    @ValueSource(strings = ["OFFENDER-DELETED"])
    internal fun `will call service for offender deletion`(eventType: String) {
      listener.processOffenderEvent(validOffenderChangedMessage(eventType))
      verify(indexListenerService).maybeDeleteOffender(
        OffenderChangedMessage(
          eventType = eventType,
          offenderIdDisplay = "A123ZZZ",
          offenderId = 2345612,
        ),
        eventType,
      )
    }

    @ParameterizedTest
    @ValueSource(strings = ["ASSESSMENT-UPDATED"])
    internal fun `will call republish message for assessment updated`(eventType: String) {
      val requestJson = validOffenderChangedMessage(eventType)
      listener.processOffenderEvent(requestJson)
      verify(offenderEventQueueService).republishMessageWithDelay(requestJson, eventType)
    }

    @ParameterizedTest
    @ValueSource(strings = ["OFFENDER_BOOKING-REASSIGNED"])
    internal fun `will call service for booking reassignment`(eventType: String) {
      val requestJson = validOffenderBookingReassignedMessage(eventType)
      listener.processOffenderEvent(requestJson)
      verify(indexListenerService).offenderBookingReassigned(
        OffenderBookingReassignedMessage(
          bookingId = 1234L,
          offenderId = 2345612L,
          previousOffenderId = 2345611L,
          offenderIdDisplay = "A123ZZZ",
          previousOffenderIdDisplay = "A123ZZZ",
        ),
        eventType,
      )
    }

    @Test
    internal fun `failed request`() {
      whenever(indexListenerService.externalMovement(any(), any())).thenThrow(RuntimeException("something went wrong"))

      assertThatThrownBy {
        listener.processOffenderEvent(
          """
        {
          "MessageId": "20e13002-d1be-56e7-be8c-66cdd7e23341",
          "Message": "{\"eventType\":\"EXTERNAL_MOVEMENT-CHANGED\", \"description\": \"some desc\", \"additionalInformation\": {\"id\":\"12345\", \"nomsNumber\":\"A7089FD\"}}",
          "MessageAttributes": {
            "eventType": {
              "Type": "String",
              "Value": "EXTERNAL_MOVEMENT-CHANGED"
            }
          }
        }
          """.trimIndent(),
        )
      }.hasMessageContaining("something went wrong")
      assertThat(logAppender.list).anyMatch { it.message.contains("Unexpected error") && it.level == Level.ERROR }
    }
  }

  private fun validExternalMovementMessage(eventType: String) = validMessage(
    eventType = eventType,
    message = """{\"eventType\":\"$eventType\", \"description\": \"some desc\", \"bookingId\": 12345, \"movementSeq\": 3, \"offenderIdDisplay\": \"A1234BC\"}""",
  )

  private fun validOffenderBookingChangedMessage(eventType: String) = validMessage(
    eventType = eventType,
    message = """{\"eventType\":\"$eventType\",\"eventDatetime\":\"2020-03-25T11:24:32.935401\",\"bookingId\":\"4323342\",\"nomisEventType\":\"S1_RESULT\"}""",
  )

  private fun validOffenderChangedMessage(eventType: String) = validMessage(
    eventType = eventType,
    message = """{\"eventType\":\"$eventType\",\"eventDatetime\":\"2020-02-25T11:24:32.935401\",\"offenderIdDisplay\":\"A123ZZZ\",\"offenderId\":\"2345612\",\"nomisEventType\":\"S1_RESULT\"}""",
  )

  private fun validOffenderBookingReassignedMessage(eventType: String) = validMessage(
    eventType = eventType,
    message = """{\"eventType\":\"$eventType\",\"eventDatetime\":\"2020-02-25T11:24:32.935401\",\"offenderIdDisplay\":\"A123ZZZ\",\"offenderId\":\"2345612\",\"previousOffenderIdDisplay\":\"A123ZZZ\",\"previousOffenderId\":\"2345611\",\"bookingId\":\"1234\",\"nomisEventType\":\"OFF_BKB_UPD\"}""",
  )

  private fun validMessage(eventType: String, message: String) = """
          {
            "Type": "Notification",
            "MessageId": "20e13002-d1be-56e7-be8c-66cdd7e23341",
            "Message": "$message",
            "MessageAttributes": {
              "eventType": {
                "Type": "String",
                "Value": "$eventType"
              },
              "id": {
                "Type": "String",
                "Value": "cb4645f2-d0c1-4677-806a-8036ed54bf69"
              }
            }
          }
  """.trimIndent()

  @Nested
  inner class BadMessages {
    @Test
    internal fun `will fail for bad json`() {
      assertThatThrownBy { listener.processOffenderEvent("this is bad json") }
        .isInstanceOf(JsonParseException::class.java)

      assertThat(logAppender.list).anyMatch { it.message.contains("Unexpected error") && it.level == Level.ERROR }
    }

    @Test
    internal fun `will ignore unknown message type`() {
      listener.processOffenderEvent(
        """
        {
          "MessageId": "20e13002-d1be-56e7-be8c-66cdd7e23341",
          "Message": "{\"eventType\":\"not.a.offender.event\", \"description\": \"some desc\", \"additionalInformation\": {\"id\":\"12345\", \"nomsNumber\":\"A7089FD\"}}",
          "MessageAttributes": {
            "eventType": {
              "Type": "String",
              "Value": "not.a.offender.event"
            }
          }
        }
        """.trimIndent(),
      )

      assertThat(logAppender.list).anyMatch { it.message.contains("message of event type") && it.level == Level.WARN }
    }
  }
}
