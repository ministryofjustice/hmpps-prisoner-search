package uk.gov.justice.digital.hmpps.prisonersearch.indexer.health

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.PrisonerBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.wiremock.PrisonApiExtension

class IndexInfoIntTest : IntegrationTestBase() {

  @Nested
  inner class WhenNoIndexesArePresent {
    @BeforeEach
    fun init() {
      deletePrisonerIndices() // required for index-size of -1
      deinitialiseIndexStatus() // required for no index status
    }

    @Test
    fun `Info page reports relevant details`() {
      webTestClient.get()
        .uri("/info")
        .exchange()
        .expectStatus()
        .isOk
        .expectBody()
        .jsonPath("index-status").value<String> { assertThat(it).contains("No status exists yet") }
        .jsonPath("index-size.count").isEqualTo(-1)
        .jsonPath("index-queue-backlog").isEqualTo("0")
    }
  }

  @Nested
  inner class WhenIndexesArePresent {

    @BeforeEach
    fun init() {
      PrisonApiExtension.prisonApi.stubOffenders(PrisonerBuilder())
      buildAndSwitchIndex(1)
    }

    @Test
    fun `Info page reports relevant details`() {
      webTestClient.get()
        .uri("/info")
        .exchange()
        .expectStatus()
        .isOk
        .expectBody()
        .jsonPath("index-status.currentIndexStartBuildTime").value<String> { assertThat(it).isNotNull() }
        .jsonPath("index-status.currentIndexEndBuildTime").value<String> { assertThat(it).isNotNull() }
        .jsonPath("index-size.count").isEqualTo(1)
        .jsonPath("index-queue-backlog").isEqualTo("0")
    }
  }
}
