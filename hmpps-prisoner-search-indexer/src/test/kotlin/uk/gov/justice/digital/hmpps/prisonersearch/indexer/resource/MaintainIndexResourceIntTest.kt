package uk.gov.justice.digital.hmpps.prisonersearch.indexer.resource

import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mockito.reset
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.PrisonerNotFoundException

class MaintainIndexResourceIntTest : IntegrationTestBase() {

  @Autowired
  @Qualifier("offenderqueue-sqs-client")
  private lateinit var offenderQueueSqsClient: SqsAsyncClient

  @BeforeEach
  fun `reset mocks`() = reset(maintainIndexService)

  @Nested
  @TestInstance(PER_CLASS)
  inner class SecureEndpoints {
    private fun secureEndpoints() = listOf(
      "/maintain-index/index-prisoner/SOME_NOMIS",
    )

    @ParameterizedTest
    @MethodSource("secureEndpoints")
    internal fun `requires a valid authentication token`(uri: String) {
      webTestClient.put()
        .uri(uri)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isUnauthorized
    }

    @ParameterizedTest
    @MethodSource("secureEndpoints")
    internal fun `requires the correct role`(uri: String) {
      webTestClient.put()
        .uri(uri)
        .headers(setAuthorisation(roles = listOf()))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isForbidden
    }
  }

  @Nested
  inner class IndexPrisoner {
    @Test
    fun `Request to index prisoner is successful and calls service`() {
      prisonerRepository.save(prisoner("A1234BC"))
      await untilCallTo { prisonerRepository.count() } matches { it == 1L }

      // wait for the index to be built
      await untilCallTo { indexQueueService.getNumberOfMessagesCurrentlyOnIndexQueue() } matches { it!! == 0 }

      webTestClient.put()
        .uri("/maintain-index/index-prisoner/A1234BC")
        .accept(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
        .exchange()
        .expectStatus().isOk

      verify(maintainIndexService).indexPrisoner("A1234BC")
    }

    @Test
    fun `Request to index unknown prisoner returns not found`() {
      doThrow(PrisonerNotFoundException("A1234BC")).whenever(maintainIndexService).indexPrisoner("A1234BC")

      webTestClient.put()
        .uri("/maintain-index/index-prisoner/A1234BC")
        .accept(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
        .exchange()
        .expectStatus().isEqualTo(404)

      verify(maintainIndexService).indexPrisoner("A1234BC")
    }
  }
}
