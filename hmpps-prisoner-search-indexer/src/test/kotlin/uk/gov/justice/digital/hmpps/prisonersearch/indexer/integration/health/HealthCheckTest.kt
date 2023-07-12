package uk.gov.justice.digital.hmpps.prisonersearch.indexer.integration.health

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.integration.wiremock.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.integration.wiremock.IncentivesApiExtension.Companion.incentivesApi
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.integration.wiremock.PrisonApiExtension.Companion.prisonApi
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.integration.wiremock.RestrictedPatientsApiExtension.Companion.restrictedPatientsApi
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.function.Consumer

class HealthCheckTest : IntegrationTestBase() {

  @Test
  fun `Health page reports ok`() {
    stubPingWithResponse(200)

    webTestClient.get()
      .uri("/health")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("status").isEqualTo("UP")
  }

  @Test
  fun `Health page reports down`() {
    stubPingWithResponse(404)

    webTestClient.get()
      .uri("/health")
      .exchange()
      .expectStatus().is5xxServerError
      .expectBody()
      .jsonPath("status").isEqualTo("DOWN")
  }

  @Test
  fun `Health info reports version`() {
    stubPingWithResponse(200)

    webTestClient.get().uri("/health")
      .exchange()
      .expectStatus().isOk
      .expectBody().jsonPath("components.healthInfo.details.version").value(
        Consumer<String> {
          assertThat(it).startsWith(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE))
        },
      )
  }

  @Test
  fun `Health ping page is accessible`() {
    webTestClient.get()
      .uri("/health/ping")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("status").isEqualTo("UP")
  }

  @Test
  fun `readiness reports ok`() {
    webTestClient.get()
      .uri("/health/readiness")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("status").isEqualTo("UP")
  }

  @Test
  fun `liveness reports ok`() {
    webTestClient.get()
      .uri("/health/liveness")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("status").isEqualTo("UP")
  }

  @Test
  fun `health check reports opensearch status`() {
    stubPingWithResponse(200)

    webTestClient.get().uri("/health")
      .exchange()
      .expectStatus().isOk
      .expectBody().jsonPath("components.openSearch.status").isEqualTo("UP")
  }

  @Test
  fun `HMPPS Domain events topic health reports UP`() {
    stubPingWithResponse(200)

    webTestClient.get()
      .uri("/health")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("status").isEqualTo("UP")
      .jsonPath("components.hmppseventtopic-health.status").isEqualTo("UP")
      .jsonPath("components.hmppseventtopic-health.details.topicArn").isEqualTo(hmppsEventTopicName)
      .jsonPath("components.hmppseventtopic-health.details.subscriptionsConfirmed").isEqualTo(0)
      .jsonPath("components.hmppseventtopic-health.details.subscriptionsPending").isEqualTo(0)
  }

  @Test
  fun `Index queue health reports UP`() {
    stubPingWithResponse(200)

    webTestClient.get()
      .uri("/health")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("status").isEqualTo("UP")
      .jsonPath("components.index-health.status").isEqualTo("UP")
      .jsonPath("components.index-health.details.queueName").isEqualTo(indexQueueName)
      .jsonPath("components.index-health.details.messagesOnQueue").isEqualTo(0)
      .jsonPath("components.index-health.details.messagesInFlight").isEqualTo(0)
      .jsonPath("components.index-health.details.dlqName").isEqualTo(indexDlqName)
      .jsonPath("components.index-health.details.dlqStatus").isEqualTo("UP")
      .jsonPath("components.index-health.details.messagesOnDlq").isEqualTo(0)
  }

  private fun stubPingWithResponse(status: Int) {
    hmppsAuth.stubHealthPing(status)
    restrictedPatientsApi.stubHealthPing(status)
    prisonApi.stubHealthPing(status)
    incentivesApi.stubHealthPing(status)
  }
}
