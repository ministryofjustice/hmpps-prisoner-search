package uk.gov.justice.digital.hmpps.prisonersearch.search.integration.health

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.info.BuildProperties
import uk.gov.justice.digital.hmpps.prisonersearch.search.integration.IntegrationTestBase

class InfoIntTest(
  @Autowired private val buildProperties: BuildProperties,
) : IntegrationTestBase() {

  @Test
  fun `Info page is accessible`() {
    webTestClient.get().uri("/info")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("build.name").isEqualTo("hmpps-prisoner-search")
  }

  @Test
  fun `Info page reports version`() {
    webTestClient.get().uri("/info")
      .exchange()
      .expectStatus().isOk
      .expectBody().jsonPath("build.version").isEqualTo(buildProperties.version)
  }
}
