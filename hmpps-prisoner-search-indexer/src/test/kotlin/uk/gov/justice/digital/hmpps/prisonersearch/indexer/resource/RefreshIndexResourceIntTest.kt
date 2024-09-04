@file:Suppress("ClassName")

package uk.gov.justice.digital.hmpps.prisonersearch.indexer.resource

import net.minidev.json.JSONArray
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.INDEX_STATUS_ID
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IndexState
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IndexStatus
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.SyncIndex
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.PrisonerBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.wiremock.PrisonApiExtension.Companion.prisonApi
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

class RefreshIndexResourceIntTest : IntegrationTestBase() {

  @BeforeEach
  fun setUp() {
    prisonApi.stubOffenders(
      PrisonerBuilder("A9999AA"),
      PrisonerBuilder("A7089EY", released = true, alertCodes = listOf("P" to "PL1"), heightCentimetres = 200),
    )
    buildAndSwitchIndex(SyncIndex.GREEN, 2)
  }

  @Test
  fun `Refresh index - no differences`() {
    indexStatusRepository.save(IndexStatus(id = INDEX_STATUS_ID, currentIndex = SyncIndex.GREEN, currentIndexState = IndexState.COMPLETED))

    reset(telemetryClient)

    webTestClient.put().uri("/refresh-index")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
      .exchange()
      .expectStatus().isAccepted

    await atMost Duration.ofSeconds(20) untilCallTo { indexSqsClient.countAllMessagesOnQueue(indexQueueUrl).get() } matches { it!! > 0 }
    await untilCallTo { indexSqsClient.countAllMessagesOnQueue(indexQueueUrl).get() } matches { it == 0 }

    verifyNoInteractions(telemetryClient)
  }

  @Test
  fun `Refresh index - unauthorised if not correct role`() {
    indexStatusRepository.save(IndexStatus(id = INDEX_STATUS_ID, currentIndex = SyncIndex.GREEN, currentIndexState = IndexState.COMPLETED))

    webTestClient.put().uri("/refresh-index")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_VIEW")))
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `Automated reconciliation - endpoint unprotected`() {
    indexStatusRepository.save(IndexStatus(id = INDEX_STATUS_ID, currentIndex = SyncIndex.GREEN, currentIndexState = IndexState.COMPLETED))

    webTestClient.put().uri("/refresh-index/automated")
      .exchange()
      .expectStatus().isAccepted

    await untilCallTo { indexSqsClient.countAllMessagesOnQueue(indexQueueUrl).get() } matches { it!! > 0 }
    await untilCallTo { indexSqsClient.countAllMessagesOnQueue(indexQueueUrl).get() } matches { it == 0 }
  }

  @Test
  fun `Reconciliation - differences`() {
    val eventCaptor = argumentCaptor<Map<String, String>>()
    val startOfTest = Instant.now()

    // Modify index record A9999AA a little
    prisonerRepository.get("A9999AA", listOf(SyncIndex.GREEN))!!.apply {
      releaseDate = LocalDate.parse("2023-01-02")
    }.also {
      prisonerRepository.save(it, SyncIndex.GREEN)
    }

    // Modify index record A7089EY a lot
    Prisoner().apply {
      prisonerNumber = "A7089EY"
      status = "ACTIVE IN"
    }.also {
      prisonerRepository.save(it, SyncIndex.GREEN)
    }

    val detailsForA9999AA = webTestClient.get().uri("/compare-index/prisoner/A9999AA")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
      .exchange()
      .expectStatus().isOk
      .expectBody<String>()
      .returnResult().responseBody

    assertThat(detailsForA9999AA).isEqualTo("""[[releaseDate: 2023-01-02, null]]""")

    val detailsForA7089EY = webTestClient.get().uri("/compare-index/prisoner/A7089EY")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
      .exchange()
      .expectStatus().isOk
      .expectBody<String>()
      .returnResult().responseBody

    assertThat(detailsForA7089EY).contains(
      "[active: true, false]",
      "[bookNumber: null, V61587]",
      "[alerts: null, [PrisonerAlert(alertType=P, alertCode=PL1, active=true, expired=false)",
      "[dateOfBirth: null, 1965-07-19]",
    )

    webTestClient.put().uri("/refresh-index")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
      .exchange()
      .expectStatus().isAccepted

    await untilCallTo { indexSqsClient.countAllMessagesOnQueue(indexQueueUrl).get() } matches { it!! > 0 }
    await untilCallTo { indexSqsClient.countAllMessagesOnQueue(indexQueueUrl).get() } matches { it == 0 }

    verify(telemetryClient, times(2)).trackEvent(
      eq("DIFFERENCE_REPORTED"),
      eventCaptor.capture(),
      isNull(),
    )

    val differences = eventCaptor.allValues.associate { it["prisonerNumber"] to it["categoriesChanged"] }
    assertThat(differences.keys).containsExactlyInAnyOrder("A9999AA", "A7089EY")
    assertThat(differences["A9999AA"]).isEqualTo("[SENTENCE]")
    assertThat(differences["A7089EY"]).isEqualTo("[ALERTS, IDENTIFIERS, LOCATION, PERSONAL_DETAILS, PHYSICAL_DETAILS, STATUS]")

    webTestClient.get().uri("/prisoner-differences?from=$startOfTest")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.[?(@.nomsNumber=='A9999AA')].differences")
      .isEqualTo("[[releaseDate: 2023-01-02, null]]")
      .jsonPath("$.[?(@.nomsNumber=='A7089EY')].differences").value<JSONArray> {
        assertThat(it.toList()).hasSize(1)
        assertThat(it.toList()[0].toString()).contains(
          "[active: true, false]",
          "[bookNumber: null, V61587]",
          "[alerts: null, [PrisonerAlert(alertType=P, alertCode=PL1, active=true, expired=false)",
          "[dateOfBirth: null, 1965-07-19]",
        )
      }
  }
}
