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
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.helpers.findLogAppender
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.IndexListenerService
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.OffenderEventQueueService

@JsonTest
internal class DomainEventListenerTest(@Autowired private val objectMapper: ObjectMapper) {
  private val indexListenerService = mock<IndexListenerService>()
  private val offenderEventQueueService: OffenderEventQueueService = mock()

  private val listener = DomainEventListener(objectMapper, indexListenerService, offenderEventQueueService)

  private val logAppender = findLogAppender(DomainEventListener::class.java)

  @Nested
  inner class ProcessIncentiveDomainEvent {
    @ParameterizedTest
    @ValueSource(
      strings = [
        "incentives.iep-review.inserted",
        "incentives.iep-review.updated",
        "incentives.iep-review.deleted",
        "incentives.prisoner.next-review-date-changed",
      ],
    )
    internal fun `will call service for iep review`(eventType: String) {
      listener.processDomainEvent(validIepDomainEvent(eventType))

      verify(indexListenerService).incentiveChange(
        IncentiveChangedMessage(
          additionalInformation = IncentiveChangeAdditionalInformation(nomsNumber = "A7089FD", id = 12345),
          eventType = eventType,
          description = "some desc",
        ),
        eventType,
      )
    }

    @Test
    internal fun `failed request`() {
      val expectedException = RuntimeException("something went wrong")
      whenever(indexListenerService.incentiveChange(any(), any())).thenThrow(expectedException)

      listener.processDomainEvent(
        """
        {
          "MessageId": "20e13002-d1be-56e7-be8c-66cdd7e23341",
          "Message": "{\"eventType\":\"incentives.iep-review.inserted\", \"description\": \"some desc\", \"additionalInformation\": {\"id\":\"12345\", \"nomsNumber\":\"A7089FD\"}}",
          "MessageAttributes": {
            "eventType": {
              "Type": "String",
              "Value": "incentives.iep-review.updated"
            }
          }
        }
        """.trimIndent(),
      )

      verify(offenderEventQueueService).handleLockingFailureOrThrow(eq(expectedException), any(), any())
    }
  }

  private fun validIepDomainEvent(eventType: String) = """
          {
            "Type": "Notification",
            "MessageId": "20e13002-d1be-56e7-be8c-66cdd7e23341",
            "Message": "{\"eventType\":\"$eventType\", \"description\": \"some desc\", \"additionalInformation\": {\"id\":\"12345\", \"nomsNumber\":\"A7089FD\"}}",
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
  inner class ProcessRestrictedPatientDomainEvent {
    @ParameterizedTest
    @ValueSource(
      strings = [
        "restricted-patients.patient.added",
        "restricted-patients.patient.removed",
        "restricted-patients.patient.supporting-prison-changed",
      ],
    )
    internal fun `will call service for restricted patient`(eventType: String) {
      listener.processDomainEvent(validRestrictedPatientDomainEvent(eventType))

      verify(indexListenerService).restrictedPatientChange(
        RestrictedPatientMessage(
          additionalInformation = RestrictedPatientAdditionalInformation(prisonerNumber = "A7089FD"),
          eventType = eventType,
          description = "some desc",
        ),
        eventType,
      )
    }

    @Test
    internal fun `failed request`() {
      val expectedException = RuntimeException("something went wrong")
      whenever(indexListenerService.restrictedPatientChange(any(), any())).thenThrow(expectedException)

      listener.processDomainEvent(
        """
        {
          "MessageId": "20e13002-d1be-56e7-be8c-66cdd7e23341",
          "Message": "{\"eventType\":\"restricted-patients.patient.added\", \"description\": \"some desc\", \"additionalInformation\": {\"id\":\"12345\", \"prisonerNumber\":\"A7089FD\"}}",
          "MessageAttributes": {
            "eventType": {
              "Type": "String",
              "Value": "restricted-patients.patient.added"
            }
          }
        }
        """.trimIndent(),
      )
      verify(offenderEventQueueService).handleLockingFailureOrThrow(eq(expectedException), any(), any())
    }
  }

  private fun validRestrictedPatientDomainEvent(eventType: String) = """
          {
            "Type": "Notification",
            "MessageId": "20e13002-d1be-56e7-be8c-66cdd7e23341",
            "Message": "{\"eventType\":\"$eventType\", \"description\": \"some desc\", \"additionalInformation\": {\"id\":\"12345\", \"prisonerNumber\":\"A7089FD\"}}",
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
      whenever(offenderEventQueueService.handleLockingFailureOrThrow(isA<JsonParseException>(), any(), any()))
        .thenThrow(RuntimeException("JsonParseException re-thrown"))

      assertThatThrownBy { listener.processDomainEvent("this is bad json") }
        .isInstanceOf(RuntimeException::class.java)

      verify(offenderEventQueueService).handleLockingFailureOrThrow(isA<JsonParseException>(), any(), any())
    }

    @Test
    internal fun `will ignore unknown message type`() {
      listener.processDomainEvent(
        """
        {
          "MessageId": "20e13002-d1be-56e7-be8c-66cdd7e23341",
          "Message": "{\"eventType\":\"not.an.iep.message\", \"description\": \"some desc\", \"additionalInformation\": {\"id\":\"12345\", \"nomsNumber\":\"A7089FD\"}}",
          "MessageAttributes": {
            "eventType": {
              "Type": "String",
              "Value": "not.an.iep.message"
            }
          }
        }
        """.trimIndent(),
      )

      assertThat(logAppender.list).anyMatch { it.message.contains("message of event type") && it.level == Level.WARN }
    }
  }
}
