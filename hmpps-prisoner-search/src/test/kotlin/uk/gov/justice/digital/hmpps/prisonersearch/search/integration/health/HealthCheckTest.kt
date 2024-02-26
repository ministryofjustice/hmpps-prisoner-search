package uk.gov.justice.digital.hmpps.prisonersearch.search.integration.health

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonersearch.search.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonersearch.search.integration.wiremock.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.digital.hmpps.prisonersearch.search.integration.wiremock.PrisonApiExtension.Companion.prisonApi

class HealthCheckTest : IntegrationTestBase() {
  @BeforeEach
  fun setup(): Unit = stubPingWithResponse(200)

  @Test
  fun `Health page reports ok`() {
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
    webTestClient.get()
      .uri("/health")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("status").isEqualTo("UP")
      .jsonPath("components.openSearch.status").isEqualTo("UP")
      .jsonPath("components.openSearch.details.cluster_name").isEqualTo("opensearch")
      .jsonPath("components.openSearch.details.timed_out").isEqualTo("false")
      .jsonPath("components.hmppsAuth.status").isEqualTo("UP")
      .jsonPath("components.prisonApi.status").isEqualTo("UP")
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
  fun `Health page reports down`() {
    stubPingWithResponse(503)

    webTestClient.get()
      .uri("/health")
      .exchange()
      .expectStatus()
      .is5xxServerError
      .expectBody()
      .jsonPath("status").isEqualTo("DOWN")
      .jsonPath("components.hmppsAuth.status").isEqualTo("DOWN")
      .jsonPath("components.prisonApi.status").isEqualTo("DOWN")
  }

  private fun stubPingWithResponse(status: Int) {
    prisonApi.stubHealthPing(status)
    hmppsAuth.stubHealthPing(status)
  }
}
