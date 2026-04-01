package uk.gov.justice.digital.hmpps.prisonersearch.indexer.health

import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.IntegrationTestBase

class IndexInfoIntTest : IntegrationTestBase() {

  @Nested
  inner class WhenNoIndexesArePresent {
    @BeforeEach
    fun init() {
      deletePrisonerIndices() // required for index-size of -1
    }

    @Test
    fun `Info page reports relevant details`() {
      webTestClient.get()
        .uri("/info")
        .exchange()
        .expectStatus()
        .isOk
        .expectBody()
        .jsonPath("index-size.count").isEqualTo(-1)
        .jsonPath("index-queue-backlog").isEqualTo("0")
    }
  }

  @Nested
  inner class WhenIndexesArePresent {

    @BeforeEach
    fun init() {
      prisonerRepository.save(Prisoner())
      await untilCallTo { prisonerRepository.count() } matches { it == 1L }
    }

    @Test
    fun `Info page reports relevant details`() {
      webTestClient.get()
        .uri("/info")
        .exchange()
        .expectStatus()
        .isOk
        .expectBody()
        .jsonPath("index-size.count").isEqualTo(1)
        .jsonPath("index-queue-backlog").isEqualTo("0")
    }
  }
}
