package uk.gov.justice.digital.hmpps.prisonersearchindexer.integration.resource

import arrow.core.left
import arrow.core.right
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mockito.reset
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.prisonersearchindexer.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonersearchindexer.integration.PrisonerBuilder
import uk.gov.justice.digital.hmpps.prisonersearchindexer.integration.wiremock.PrisonApiExtension
import uk.gov.justice.digital.hmpps.prisonersearchindexer.model.IndexState.BUILDING
import uk.gov.justice.digital.hmpps.prisonersearchindexer.model.IndexState.CANCELLED
import uk.gov.justice.digital.hmpps.prisonersearchindexer.model.IndexState.COMPLETED
import uk.gov.justice.digital.hmpps.prisonersearchindexer.model.IndexStatus
import uk.gov.justice.digital.hmpps.prisonersearchindexer.model.SyncIndex.GREEN
import uk.gov.justice.digital.hmpps.prisonersearchindexer.services.BuildAlreadyInProgressError
import uk.gov.justice.digital.hmpps.prisonersearchindexer.services.BuildNotInProgressError
import uk.gov.justice.digital.hmpps.prisonersearchindexer.services.IndexService
import uk.gov.justice.digital.hmpps.prisonersearchindexer.services.NoActiveIndexesError
import uk.gov.justice.digital.hmpps.prisonersearchindexer.services.PrisonerNotFoundError

class MaintainIndexResourceApiTest : IntegrationTestBase() {
  @SpyBean
  private lateinit var indexService: IndexService

  @BeforeEach
  fun `reset mocks`() = reset(indexService)

  @Nested
  @TestInstance(PER_CLASS)
  inner class SecureEndpoints {
    private fun secureEndpoints() =
      listOf(
        "/maintain-index/build",
        "/maintain-index/mark-complete",
        "/maintain-index/switch",
        "/maintain-index/cancel",
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
  inner class BuildIndex {
    @Test
    fun `Request build index is successful and calls service`() {
      doReturn(IndexStatus(currentIndex = GREEN, otherIndexState = BUILDING).right()).whenever(indexService)
        .prepareIndexForRebuild()

      webTestClient.put()
        .uri("/maintain-index/build")
        .accept(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.otherIndex").isEqualTo("BLUE")
        .jsonPath("$.otherIndexState").isEqualTo("BUILDING")

      verify(indexService).prepareIndexForRebuild()
    }

    @Test
    fun `Request build index already building returns conflict`() {
      val expectedIndexStatus = IndexStatus(currentIndex = GREEN, otherIndexState = BUILDING)
      doReturn(BuildAlreadyInProgressError(expectedIndexStatus).left()).whenever(indexService).prepareIndexForRebuild()

      webTestClient.put()
        .uri("/maintain-index/build")
        .accept(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
        .exchange()
        .expectStatus().isEqualTo(409)
        .expectBody()
        .jsonPath("$.userMessage").value<String> { message ->
          assertThat(message).contains(expectedIndexStatus.otherIndex.name)
          assertThat(message).contains(expectedIndexStatus.otherIndexState.name)
        }

      verify(indexService).prepareIndexForRebuild()
    }
  }

  @Nested
  inner class MarkIndexComplete {
    @Test
    fun `Request to mark index complete is successful and calls service`() {
      doReturn(anIndexStatus().right()).whenever(indexService).markIndexingComplete(ignoreThreshold = false)

      webTestClient.put()
        .uri("/maintain-index/mark-complete")
        .accept(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
        .exchange()
        .expectStatus().isOk

      verify(indexService).markIndexingComplete(ignoreThreshold = false)
    }

    @Test
    fun `Request to mark index complete when index not building returns error`() {
      val expectedIndexStatus = IndexStatus(currentIndex = GREEN, otherIndexState = COMPLETED)
      doReturn(BuildNotInProgressError(expectedIndexStatus).left()).whenever(indexService).markIndexingComplete(
        ignoreThreshold = false,
      )

      webTestClient.put()
        .uri("/maintain-index/mark-complete")
        .accept(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
        .exchange()
        .expectStatus().isEqualTo(409)
        .expectBody()
        .jsonPath("$.userMessage").value<String> { message ->
          assertThat(message).contains(expectedIndexStatus.otherIndex.name)
          assertThat(message).contains(expectedIndexStatus.otherIndexState.name)
        }

      verify(indexService).markIndexingComplete(ignoreThreshold = false)
    }
  }

  @Nested
  inner class SwitchIndex {
    @Test
    fun `Request to mark index complete is successful and calls service`() {
      doReturn(anIndexStatus().right()).whenever(indexService).switchIndex(false)

      webTestClient.put()
        .uri("/maintain-index/switch")
        .accept(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
        .exchange()
        .expectStatus().isOk

      verify(indexService).switchIndex(false)
    }
  }

  @Nested
  inner class CancelIndexing {
    @Test
    fun `Request to cancel indexing is successful and calls service`() {
      doReturn(anIndexStatus().right()).whenever(indexService).cancelIndexing()

      webTestClient.put()
        .uri("/maintain-index/cancel")
        .accept(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
        .exchange()
        .expectStatus().isOk

      verify(indexService).cancelIndexing()
    }

    @Test
    fun `Request to mark index cancelled when index not building returns error`() {
      val expectedIndexStatus = IndexStatus(currentIndex = GREEN, otherIndexState = CANCELLED)
      doReturn(BuildNotInProgressError(expectedIndexStatus).left()).whenever(indexService).cancelIndexing()

      webTestClient.put()
        .uri("/maintain-index/cancel")
        .accept(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
        .exchange()
        .expectStatus().isEqualTo(409)
        .expectBody()
        .jsonPath("$.userMessage").value<String> { message ->
          assertThat(message).contains(expectedIndexStatus.otherIndex.name)
          assertThat(message).contains(expectedIndexStatus.otherIndexState.name)
        }

      verify(indexService).cancelIndexing()
    }
  }

  @Nested
  inner class IndexPrisoner {
    @Test
    fun `Request to index prisoner is successful and calls service`() {
      PrisonApiExtension.prisonApi.stubOffenders(PrisonerBuilder("A1234BC"))
      buildAndSwitchIndex(GREEN, 1)

      webTestClient.put()
        .uri("/maintain-index/index-prisoner/A1234BC")
        .accept(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
        .exchange()
        .expectStatus().isOk

      verify(indexService).updatePrisoner("A1234BC")
    }

    @Test
    fun `Request to index prisoner without active indexes returns conflict`() {
      val expectedIndexStatus = IndexStatus.newIndex()
      doReturn(NoActiveIndexesError(expectedIndexStatus).left()).whenever(indexService).updatePrisoner("A1234BC")

      webTestClient.put()
        .uri("/maintain-index/index-prisoner/A1234BC")
        .accept(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
        .exchange()
        .expectStatus().isEqualTo(409)

      verify(indexService).updatePrisoner("A1234BC")
    }

    @Test
    fun `Request to index unknown prisoner returns not found`() {
      doReturn(PrisonerNotFoundError("A1234BC").left()).whenever(indexService).updatePrisoner("A1234BC")

      webTestClient.put()
        .uri("/maintain-index/index-prisoner/A1234BC")
        .accept(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
        .exchange()
        .expectStatus().isEqualTo(404)

      verify(indexService).updatePrisoner("A1234BC")
    }
  }

  @Nested
  inner class IndexHouseKeeping {

    @BeforeEach
    fun mockService() {
      doReturn(IndexStatus("any_id", GREEN).right()).whenever(indexService).markIndexingComplete(ignoreThreshold = false)
    }

    @Test
    fun `endpoint is not secured`() {
      webTestClient.put()
        .uri("/maintain-index/check-complete")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isOk
    }

    @Test
    fun `attempts to mark the build as complete`() {
      webTestClient.put()
        .uri("/maintain-index/check-complete")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()

      verify(indexService).markIndexingComplete(ignoreThreshold = false)
    }
  }
}

fun anIndexStatus() = IndexStatus.newIndex()
