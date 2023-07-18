package uk.gov.justice.digital.hmpps.prisonersearch.search.integration.health

import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonersearch.search.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonersearch.search.integration.wiremock.HmppsAuthApiExtension.Companion.hmppsAuth

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
  fun `Health page shows associated services`() {
    stubPingWithResponse(200)

    webTestClient.get()
      .uri("/health")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("status").isEqualTo("UP")
      .jsonPath("components.hmppsAuth.status").isEqualTo("UP")
      .jsonPath("components.openSearch.status").isEqualTo("UP")
      .jsonPath("components.openSearch.details.cluster_name").isEqualTo("opensearch")
      .jsonPath("components.openSearch.details.timed_out").isEqualTo("false")
  }

  @Test
  fun `Health ping page is accessible`() {
    stubPingWithResponse(200)

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
    stubPingWithResponse(200)

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
    stubPingWithResponse(200)

    webTestClient.get()
      .uri("/health/liveness")
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
      .expectStatus()
      .is5xxServerError
      .expectBody()
      .jsonPath("status").isEqualTo("DOWN")
  }

  @Test
  fun `Health page reports a teapot`() {
    stubPingWithResponse(418)

    webTestClient.get()
      .uri("/health")
      .exchange()
      .expectStatus()
      .is5xxServerError
      .expectBody()
      .jsonPath("status").isEqualTo("DOWN")
  }

  private fun stubPingWithResponse(status: Int) {
    hmppsAuth.stubHealthPing(status)
  }
}
