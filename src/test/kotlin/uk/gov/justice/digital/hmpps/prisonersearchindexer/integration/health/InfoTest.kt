package uk.gov.justice.digital.hmpps.prisonersearchindexer.integration.health

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonersearchindexer.integration.IntegrationTestBase
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class InfoTest : IntegrationTestBase() {

  @Test
  fun `Info page is accessible`() {
    webTestClient.get().uri("/info")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("build.name").isEqualTo("hmpps-prisoner-search-indexer")
  }

  @Test
  fun `Info page reports version`() {
    webTestClient.get().uri("/info")
      .exchange()
      .expectStatus().isOk
      .expectBody().jsonPath("build.version").value<String> {
        assertThat(it).startsWith(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE))
      }
  }

  @Test
  fun `Info page reports index status`() {
    webTestClient.get().uri("/info")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("index-status.id").isEqualTo("STATUS")
      .jsonPath("index-status.inProgress").isEqualTo("false")
  }
}
