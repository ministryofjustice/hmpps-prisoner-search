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
import org.mockito.kotlin.eq
import org.mockito.kotlin.isA
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.json.JsonTest
import org.springframework.dao.OptimisticLockingFailureException
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.helpers.findLogAppender
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.BookingDeletedMessage
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.ExternalPrisonerMovementMessage
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.IndexListenerService
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.OffenderBookingChangedMessage
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.OffenderBookingReassignedMessage
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.OffenderChangedMessage
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.OffenderEventQueueService
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.OffenderEventQueueService.RequeueDestination
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.PrisonerLocationChangedMessage

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
    fun `will call service for external movement`(eventType: String) {
      listener.processOffenderEvent(validExternalMovementMessage(eventType))
      verify(indexListenerService).externalMovement(ExternalPrisonerMovementMessage(bookingId = 12345L), eventType)
    }

    @ParameterizedTest
    @ValueSource(
      strings = [
        "OFFENDER_BOOKING-CHANGED", "IMPRISONMENT_STATUS-CHANGED",
        "BED_ASSIGNMENT_HISTORY-INSERTED", "SENTENCE_DATES-CHANGED", "CONFIRMED_RELEASE_DATE-CHANGED",
        "ASSESSMENT-CHANGED", "OFFENDER_PROFILE_DETAILS-INSERTED", "OFFENDER_PROFILE_DETAILS-UPDATED",
        "SENTENCING-CHANGED",
      ],
    )
    fun `will call service for booking change`(eventType: String) {
      listener.processOffenderEvent(validOffenderBookingChangedMessage(eventType))
      verify(indexListenerService).offenderBookingChange(OffenderBookingChangedMessage(bookingId = 4323342L), eventType)
    }

    @ParameterizedTest
    @ValueSource(strings = ["BOOKING_NUMBER-CHANGED"])
    fun `will call service for booking number change`(eventType: String) {
      listener.processOffenderEvent(validOffenderBookingChangedMessage(eventType))
      verify(indexListenerService).offenderBookNumberChange(
        OffenderBookingChangedMessage(bookingId = 4323342L),
        eventType,
      )
    }

    @ParameterizedTest
    @ValueSource(
      strings = [
        "OFFENDER-INSERTED", "OFFENDER-UPDATED", "OFFENDER_DETAILS-CHANGED", "OFFENDER_ALIAS-CHANGED",
        "KEY_DATE_ADJUSTMENT_UPSERTED", "KEY_DATE_ADJUSTMENT_DELETED",
        "OFFENDER_PHYSICAL_DETAILS-CHANGED", "OFFENDER_IDENTIFIER-UPDATED", "ASSESSMENT-UPDATED_REPUBLISHED",
        "OFFENDER_CHARGES-UPDATED", "OFFENDER_CHARGES-INSERTED", "OFFENDER_CHARGES-DELETED",
        "OFFENDER_ADDRESS-INSERTED", "OFFENDER_ADDRESS-UPDATED", "OFFENDER_EMAIL-INSERTED", "OFFENDER_EMAIL-DELETED",
        "OFFENDER_EMAIL-UPDATED", "OFFENDER_PHONE-INSERTED", "OFFENDER_PHONE-DELETED", "OFFENDER_PHONE-UPDATED",
        "OFFENDER_ADDRESS_PHONE-INSERTED", "OFFENDER_ADDRESS_PHONE-UPDATED", "OFFENDER_ADDRESS_PHONE-DELETED",
        "OFFENDER_PHYSICAL_ATTRIBUTES-CHANGED", "OFFENDER_IDENTIFYING_MARKS-CHANGED", "OFFENDER_IDENTIFYING_MARKS-DELETED",
        "OFFENDER_ADDRESS-DELETED",
        "SENTENCE_ADJUSTMENT_UPSERTED", "SENTENCE_ADJUSTMENT_DELETED",
        "OFFENDER_MARKS_IMAGE-CREATED", "OFFENDER_MARKS_IMAGE-UPDATED", "OFFENDER_MARKS_IMAGE-DELETED",
        "OFFENDER_IMAGE-CREATED", "OFFENDER_IMAGE-UPDATED", "OFFENDER_IMAGE-DELETED",
        "OFF_HEALTH_PROBLEMS-INSERTED", "OFF_HEALTH_PROBLEMS-UPDATED", "OFF_HEALTH_PROBLEMS-DELETED",
        "OFFENDER_LANGUAGES-INSERTED", "OFFENDER_LANGUAGES-UPDATED", "OFFENDER_LANGUAGES-DELETED",
      ],
    )
    fun `will call service for offender change`(eventType: String) {
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
    fun `will call service for offender deletion`(eventType: String) {
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

    @Test
    fun `will call service for booking deletion`() {
      listener.processOffenderEvent(validBookingDeletedMessage())
      verify(indexListenerService).bookingDeleted(
        BookingDeletedMessage(
          offenderIdDisplay = "A123ZZZ",
          bookingId = 1234,
        ),
        "BOOKING-DELETED",
      )
    }

    @ParameterizedTest
    @ValueSource(strings = ["ASSESSMENT-UPDATED"])
    fun `will call republish message for assessment updated`(eventType: String) {
      val requestJson = validOffenderChangedMessage(eventType)
      listener.processOffenderEvent(requestJson)
      verify(offenderEventQueueService).republishMessageWithDelay(requestJson, eventType)
    }

    @ParameterizedTest
    @ValueSource(strings = ["OFFENDER_BOOKING-REASSIGNED"])
    fun `will call service for booking reassignment`(eventType: String) {
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

    @ParameterizedTest
    @ValueSource(strings = ["AGENCY_INTERNAL_LOCATIONS-UPDATED"])
    fun `will process an agency internal locations updated message`(eventType: String) {
      val requestJson = validPrisonerLocationChangeMessage(eventType)
      listener.processOffenderEvent(requestJson)
      verify(indexListenerService).prisonerLocationChange(
        PrisonerLocationChangedMessage(
          oldDescription = "EWI-RES1-2-14",
          prisonId = "EWI",
        ),
        eventType,
      )
    }

    @Test
    fun `failed request`() {
      val expectedException = RuntimeException("something went wrong")
      whenever(indexListenerService.externalMovement(any(), any())).thenThrow(expectedException)

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

      verify(offenderEventQueueService).handleLockingFailureOrThrow(eq(expectedException), any(), any())
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

  private fun validBookingDeletedMessage() = validMessage(
    eventType = "BOOKING-DELETED",
    message = """{\"eventType\":\"BOOKING-DELETED\",\"eventDatetime\":\"2025-02-25T11:24:32.935401\",\"offenderIdDisplay\":\"A123ZZZ\",\"offenderId\":\"2345612\",\"bookingId\":\"1234\",\"nomisEventType\":\"BOOKING-DELETED\"}""",
  )

  private fun validPrisonerLocationChangeMessage(eventType: String) = validMessage(
    eventType = eventType,
    message = """{\"eventType\":\"$eventType\",\"eventDatetime\":\"2020-02-25T11:24:32.935401\",\"oldDescription\":\"EWI-RES1-2-14\",\"nomisEventType\":\"AGENCY_INTERNAL_LOCATIONS-UPDATED\",\"recordDeleted\":\"false\",\"prisonId\":\"EWI\",\"description\":\"EWI-RES1-2-014\",\"auditModuleName\":\"OIMILOCA\",\"internalLocationId\":\"633621\"}""",
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
    fun `will fail for bad json`() {
      whenever(offenderEventQueueService.handleLockingFailureOrThrow(isA<JsonParseException>(), any(), any()))
        .thenThrow(RuntimeException("JsonParseException re-thrown"))

      assertThatThrownBy { listener.processOffenderEvent("this is bad json") }
        .isInstanceOf(RuntimeException::class.java)

      verify(offenderEventQueueService).handleLockingFailureOrThrow(isA<JsonParseException>(), any(), any())
    }

    @Test
    fun `will requeue for version number clash`() {
      val event = "OFFENDER_ADDRESS-INSERTED"
      val message =
        """{\"eventType\":\"$event\", \"description\": \"desc\", \"additionalInformation\": {\"id\":\"12345\", \"nomsNumber\":\"A7089FD\"}}"""
      val expectedException =
        OptimisticLockingFailureException("stack trace ... Cannot index a document due to seq_no+primary_term conflict ..")
      whenever(indexListenerService.offenderChange(any(), any()))
        .thenThrow(expectedException)

      val requestJson =
        """
        {
          "MessageId": "20e13002-d1be-56e7-be8c-66cdd7e23341",
          "Message": "$message",
          "MessageAttributes": {
            "eventType": {
              "Type": "String",
              "Value": "$event"
            }
          }
        }
        """.trimIndent()

      listener.processOffenderEvent(requestJson)

      verify(offenderEventQueueService).handleLockingFailureOrThrow(
        expectedException,
        RequeueDestination.OFFENDER,
        requestJson,
      )
    }

    @Test
    fun `will ignore unknown message type`() {
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
