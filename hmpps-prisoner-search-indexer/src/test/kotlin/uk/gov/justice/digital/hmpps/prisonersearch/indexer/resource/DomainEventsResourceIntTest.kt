@file:OptIn(ExperimentalCoroutinesApi::class)

package uk.gov.justice.digital.hmpps.prisonersearch.indexer.resource

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.BodyInserters
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.MsgBody
import uk.gov.justice.hmpps.sqs.MissingQueueException
import uk.gov.justice.hmpps.sqs.PurgeQueueRequest
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue

class DomainEventsResourceIntTest : IntegrationTestBase() {
  @Autowired
  private lateinit var objectMapper: ObjectMapper

  private val hmppsEventsQueue by lazy {
    hmppsQueueService.findByQueueId("hmppseventtestqueue")
      ?: throw MissingQueueException("hmppseventtestqueue queue not found")
  }

  @BeforeEach
  fun purgeHmppsEventsQueue() = runTest {
    with(hmppsEventsQueue) {
      hmppsQueueService.purgeQueue(PurgeQueueRequest(queueName, sqsClient, queueUrl))
    }
  }

  @Test
  fun `access forbidden when no authority`() {
    webTestClient
      .put()
      .uri("/events/prisoner/received/A2483AA")
      .contentType(MediaType.APPLICATION_JSON)
      .body(
        BodyInserters.fromValue(
          """
        {
          "reason": "TRANSFERRED",
          "prisonId": "WWI",
          "occurredAt": "2020-07-19T12:30:12"
        }
      """,
        ),
      )
      .exchange()
      .expectStatus()
      .isUnauthorized
  }

  @Test
  fun `access forbidden when no role`() {
    webTestClient
      .put()
      .uri("/events/prisoner/received/A2483AA")
      .contentType(MediaType.APPLICATION_JSON)
      .body(
        BodyInserters.fromValue(
          """
        {
          "reason": "TRANSFERRED",
          "prisonId": "WWI",
          "occurredAt": "2020-07-19T12:30:12"
        }
      """,
        ),
      )
      .headers(setAuthorisation())
      .exchange()
      .expectStatus()
      .isForbidden
  }

  @Test
  fun `reason code must be valid`() {
    webTestClient
      .put()
      .uri("/events/prisoner/received/A2483AA")
      .contentType(MediaType.APPLICATION_JSON)
      .body(
        BodyInserters.fromValue(
          """
        {
          "reason": "BANANAS",
          "prisonId": "WWI",
          "occurredAt": "2020-07-19T12:30:12"
        }
      """,
        ),
      )
      .headers(setAuthorisation(roles = listOf("ROLE_EVENTS_ADMIN")))
      .exchange()
      .expectStatus()
      .isBadRequest
  }

  @Test
  fun `occurredAt must be present`() {
    webTestClient
      .put()
      .uri("/events/prisoner/received/A2483AA")
      .contentType(MediaType.APPLICATION_JSON)
      .body(
        BodyInserters.fromValue(
          """
        {
          "reason": "TRANSFERRED",
          "prisonId": "WWI"
        }
      """,
        ),
      )
      .headers(setAuthorisation(roles = listOf("ROLE_EVENTS_ADMIN")))
      .exchange()
      .expectStatus()
      .isBadRequest
  }

  @Test
  fun `prisonId must be present`() {
    webTestClient
      .put()
      .uri("/events/prisoner/received/A2483AA")
      .contentType(MediaType.APPLICATION_JSON)
      .body(
        BodyInserters.fromValue(
          """
        {
          "reason": "TRANSFERRED",
          "occurredAt": "2020-07-19T12:30:12"
        }
      """,
        ),
      )
      .headers(setAuthorisation(roles = listOf("ROLE_EVENTS_ADMIN")))
      .exchange()
      .expectStatus()
      .isBadRequest
  }

  @Test
  fun `sends prisoner receive event to the domain topic`() {
    webTestClient
      .put()
      .uri("/events/prisoner/received/A2483AA")
      .contentType(MediaType.APPLICATION_JSON)
      .body(
        BodyInserters.fromValue(
          """
        {
          "reason": "TRANSFERRED",
          "prisonId": "WWI",
          "occurredAt": "2020-07-19T12:30:12"
        }
      """,
        ),
      )
      .headers(setAuthorisation(roles = listOf("ROLE_EVENTS_ADMIN")))
      .exchange()
      .expectStatus()
      .isAccepted

    await untilCallTo { getNumberOfMessagesCurrentlyOnDomainQueue() } matches { it == 1 }

    val message = readNextDomainEventMessage()

    assertThatJson(message).node("eventType").isEqualTo("test.prisoner-offender-search.prisoner.received")
    assertThatJson(message).node("version").isEqualTo(1)
    assertThatJson(message).node("occurredAt").isEqualTo("2020-07-19T12:30:12+01:00")
    assertThatJson(message).node("additionalInformation.nomsNumber").isEqualTo("A2483AA")
    assertThatJson(message).node("additionalInformation.reason").isEqualTo("TRANSFERRED")
  }

  private fun readNextDomainEventMessage(): String {
    val updateResult = hmppsEventsQueue.sqsClient.receiveMessage(
      ReceiveMessageRequest.builder().queueUrl(hmppsEventsQueue.queueUrl).build(),
    ).get().messages().first()
    hmppsEventsQueue.sqsClient.deleteMessage(
      DeleteMessageRequest.builder().queueUrl(hmppsEventsQueue.queueUrl).receiptHandle(updateResult.receiptHandle()).build(),
    ).get()
    return objectMapper.readValue<MsgBody>(updateResult.body()).Message
  }

  private fun getNumberOfMessagesCurrentlyOnDomainQueue(): Int? = hmppsEventsQueue.sqsClient.countAllMessagesOnQueue(hmppsEventsQueue.queueUrl).get()
}
