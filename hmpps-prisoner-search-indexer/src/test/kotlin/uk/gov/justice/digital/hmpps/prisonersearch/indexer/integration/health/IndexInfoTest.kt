package uk.gov.justice.digital.hmpps.prisonersearch.indexer.integration.health

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.SyncIndex
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.integration.PrisonerBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.integration.wiremock.PrisonApiExtension

class IndexInfoTest : IntegrationTestBase() {

  @Nested
  inner class WhenNoIndexesArePresent {
    @BeforeEach
    fun init() {
      deletePrisonerIndices()
      deinitialiseIndexStatus()
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
        .jsonPath("index-status.currentIndex").doesNotExist()
        .jsonPath("index-status.otherIndex").doesNotExist()
        .jsonPath("index-size.GREEN").isEqualTo(-1)
        .jsonPath("index-size.BLUE").isEqualTo(-1)
        .jsonPath("prisoner-alias").isEqualTo("")
        .jsonPath("index-queue-backlog").isEqualTo("0")
    }
  }

  @Nested
  inner class WhenIndexesArePresent {

    @BeforeEach
    fun init() {
      PrisonApiExtension.prisonApi.stubOffenders(PrisonerBuilder())
      buildAndSwitchIndex(SyncIndex.GREEN, 1)
    }

    @Test
    fun `Info page reports relevant details`() {
      webTestClient.get()
        .uri("/info")
        .exchange()
        .expectStatus()
        .isOk
        .expectBody()
        .jsonPath("index-status.currentIndex").isEqualTo("GREEN")
        .jsonPath("index-status.currentIndexStartBuildTime").value<String> { assertThat(it).isNotNull() }
        .jsonPath("index-status.currentIndexEndBuildTime").value<String> { assertThat(it).isNotNull() }
        .jsonPath("index-status.otherIndex").isEqualTo("BLUE")
        .jsonPath("index-status.otherIndexStartBuildTime").doesNotExist()
        .jsonPath("index-status.otherIndexEndBuildTime").doesNotExist()
        .jsonPath("index-size.GREEN").isEqualTo(1)
        .jsonPath("index-size.BLUE").isEqualTo(0)
        .jsonPath("prisoner-alias").isEqualTo("prisoner-search-green")
        .jsonPath("index-queue-backlog").isEqualTo("0")
    }
  }
}
