@file:OptIn(ExperimentalCoroutinesApi::class)

package uk.gov.justice.digital.hmpps.prisonersearch.indexer.resource

import kotlinx.coroutines.ExperimentalCoroutinesApi
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.IntegrationTestBase

class DomainEventsResourceIntTest : IntegrationTestBase() {
  @BeforeEach
  fun purgeHmppsEventsQueue() = purgeDomainEventsQueue()

  @Nested
  inner class Received {

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
  }

  @Nested
  inner class Released {

    @Test
    fun `access forbidden when no authority`() {
      webTestClient
        .put()
        .uri("/events/prisoner/released/A2483AA")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
          BodyInserters.fromValue(
            """
        {
          "reason": "SENT_TO_COURT",
          "prisonId": "WWI"
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
        .uri("/events/prisoner/released/A2483AA")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
          BodyInserters.fromValue(
            """
        {
          "reason": "SENT_TO_COURT",
          "prisonId": "WWI"
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
        .uri("/events/prisoner/released/A2483AA")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
          BodyInserters.fromValue(
            """
        {
          "reason": "BANANAS",
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
        .uri("/events/prisoner/released/A2483AA")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
          BodyInserters.fromValue(
            """
        {
          "reason": "SENT_TO_COURT"
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
    fun `sends prisoner released event to the domain topic`() {
      webTestClient
        .put()
        .uri("/events/prisoner/released/A2483AA")
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
        .isAccepted

      await untilCallTo { getNumberOfMessagesCurrentlyOnDomainQueue() } matches { it == 1 }

      val message = readNextDomainEventMessage()

      assertThatJson(message).node("eventType").isEqualTo("test.prisoner-offender-search.prisoner.released")
      assertThatJson(message).node("version").isEqualTo(1)
      assertThatJson(message).node("additionalInformation.nomsNumber").isEqualTo("A2483AA")
      assertThatJson(message).node("additionalInformation.reason").isEqualTo("TRANSFERRED")
      assertThatJson(message).node("additionalInformation.prisonId").isEqualTo("WWI")
    }
  }

  @Nested
  inner class Created {
    @Test
    fun `access forbidden when no authority`() {
      webTestClient
        .put()
        .uri("/events/prisoner/created/A2483AA")
        .exchange()
        .expectStatus()
        .isUnauthorized
    }

    @Test
    fun `access forbidden when no role`() {
      webTestClient
        .put()
        .uri("/events/prisoner/created/A2483AA")
        .headers(setAuthorisation())
        .exchange()
        .expectStatus()
        .isForbidden
    }

    @Test
    fun `sends prisoner created event to the domain topic`() {
      webTestClient
        .put()
        .uri("/events/prisoner/created/A2483AA")
        .headers(setAuthorisation(roles = listOf("ROLE_EVENTS_ADMIN")))
        .exchange()
        .expectStatus()
        .isAccepted

      await untilCallTo { getNumberOfMessagesCurrentlyOnDomainQueue() } matches { it == 1 }

      val message = readNextDomainEventMessage()

      assertThatJson(message).node("eventType").isEqualTo("test.prisoner-offender-search.prisoner.created")
      assertThatJson(message).node("version").isEqualTo(1)
      assertThatJson(message).node("occurredAt").isEqualTo("2022-09-16T11:40:34+01:00") // the fixed clock time
      assertThatJson(message).node("additionalInformation.nomsNumber").isEqualTo("A2483AA")
    }
  }

  @Nested
  inner class Removed {
    @Test
    fun `access forbidden when no authority`() {
      webTestClient
        .put()
        .uri("/events/prisoner/removed/A2483AA")
        .exchange()
        .expectStatus()
        .isUnauthorized
    }

    @Test
    fun `access forbidden when no role`() {
      webTestClient
        .put()
        .uri("/events/prisoner/removed/A2483AA")
        .headers(setAuthorisation())
        .exchange()
        .expectStatus()
        .isForbidden
    }

    @Test
    fun `sends prisoner removed event to the domain topic`() {
      webTestClient
        .put()
        .uri("/events/prisoner/removed/A2483AA")
        .headers(setAuthorisation(roles = listOf("ROLE_EVENTS_ADMIN")))
        .exchange()
        .expectStatus()
        .isAccepted

      await untilCallTo { getNumberOfMessagesCurrentlyOnDomainQueue() } matches { it == 1 }

      val message = readNextDomainEventMessage()

      assertThatJson(message).node("eventType").isEqualTo("test.prisoner-offender-search.prisoner.removed")
      assertThatJson(message).node("version").isEqualTo(1)
      assertThatJson(message).node("occurredAt").isEqualTo("2022-09-16T11:40:34+01:00") // the fixed clock time
      assertThatJson(message).node("additionalInformation.nomsNumber").isEqualTo("A2483AA")
    }
  }
}
