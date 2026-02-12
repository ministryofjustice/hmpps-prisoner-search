@file:Suppress("ClassName")

package uk.gov.justice.digital.hmpps.prisonersearch.indexer.resource

import net.minidev.json.JSONArray
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.PrisonerBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.wiremock.AlertsApiExtension.Companion.alertsApi
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.wiremock.ComplexityOfNeedApiExtension.Companion.complexityOfNeedApi
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.wiremock.PrisonApiExtension.Companion.prisonApi
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.wiremock.PrisonRegisterApiExtension.Companion.prisonRegisterApi
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue
import java.time.Instant
import java.time.LocalDate

class RefreshIndexResourceIntTest : IntegrationTestBase() {
  @BeforeEach
  fun setUp() {
    prisonApi.stubOffenders(
      PrisonerBuilder("A9999AA"),
      PrisonerBuilder("A7089EY", released = true, heightCentimetres = 200),
    )
    alertsApi.stubSuccess("A1239DD", listOf("P" to "PL1"))
    complexityOfNeedApi.stubSuccess("A9999AA", "low")
    complexityOfNeedApi.stubSuccess("A7089EY", "high")
    prisonRegisterApi.stubGetPrisons() // NOTE response is cached
    buildAndSwitchIndex(2)
    purgeDomainEventsQueue()
  }

  @Nested
  inner class RefreshIndex {

    @Test
    fun `Refresh index - no differences`() {
      reset(telemetryClient)

      webTestClient.put().uri("/refresh-index")
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
        .exchange()
        .expectStatus().isAccepted

      await untilCallTo { indexSqsClient.countAllMessagesOnQueue(indexQueueUrl).get() } matches { it!! > 0 }
      await untilCallTo { indexSqsClient.countAllMessagesOnQueue(indexQueueUrl).get() } matches { it == 0 }

      verifyNoInteractions(telemetryClient)
    }

    @Test
    fun `Refresh index - unauthorised if not correct role`() {
      webTestClient.put().uri("/refresh-index")
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_VIEW")))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `Reconciliation - differences`() {
      val eventCaptor = argumentCaptor<Map<String, String>>()
      val startOfTest = Instant.now()

      // Modify index record A9999AA a little
      prisonerRepository.get("A9999AA")!!.apply {
        releaseDate = LocalDate.parse("2023-01-02")
      }.also {
        prisonerRepository.save(it)
      }
      // Modify index record A7089EY a lot
      Prisoner().apply {
        prisonerNumber = "A7089EY"
        status = "ACTIVE IN"
        prisonId = "MDI"
        restrictedPatient = false
        complexityOfNeedLevel = "medium"
      }.also {
        prisonerRepository.save(it)
      }

      // A7089EY complexityOfNeed is changing from a value to null
      complexityOfNeedApi.stubNotFound("A7089EY")

      val detailsForA9999AA = webTestClient.get().uri("/compare-index/prisoner/A9999AA")
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
        .exchange()
        .expectStatus().isOk
        .expectBody<String>()
        .returnResult().responseBody

      assertThat(detailsForA9999AA).isEqualTo("""[[releaseDate: 2023-01-02, null]]""")

      alertsApi.stubSuccess("A9999AA")

      val detailsForA7089EY = webTestClient.get().uri("/compare-index/prisoner/A7089EY")
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
        .exchange()
        .expectStatus().isOk
        .expectBody<String>()
        .returnResult().responseBody

      assertThat(detailsForA7089EY).contains(
        "[active: true, false]",
        "[bookNumber: null, V61587]",
        "[croNumber: null, 29906/12L]",
        "[dateOfBirth: null, 1965-07-19]",
        "[complexityOfNeedLevel: medium, null]",
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
      assertThat(differences["A9999AA"]).isEqualTo("[ALERTS, SENTENCE]")
      assertThat(differences["A7089EY"]).isEqualTo("[IDENTIFIERS, LOCATION, PERSONAL_DETAILS, PHYSICAL_DETAILS, STATUS]")

      webTestClient.get().uri("/prisoner-differences?from=$startOfTest")
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.[?(@.nomsNumber=='A9999AA')].differences").value<JSONArray> {
          assertThat(it.toList()).hasSize(1)
          assertThat(it.toList()[0].toString()).contains(
            "[releaseDate: 2023-01-02, null]",
            "[alerts: null, [PrisonerAlert(alertType=A, alertCode=ABC, active=true, expired=true)]]",
          )
        }
        .jsonPath("$.[?(@.nomsNumber=='A7089EY')].differences").value<JSONArray> {
          assertThat(it.toList()).hasSize(1)
          assertThat(it.toList()[0].toString()).contains(
            "[active: true, false]",
            "[bookNumber: null, V61587]",
            "[dateOfBirth: null, 1965-07-19]",
          )
        }

      await untilCallTo { getNumberOfMessagesCurrentlyOnDomainQueue() } matches { it == 4 }

      assertThat(
        listOf(
          readEventFromNextDomainEventMessage(),
          readEventFromNextDomainEventMessage(),
          readEventFromNextDomainEventMessage(),
          readEventFromNextDomainEventMessage(),
        ),
      ).containsExactlyInAnyOrder(
        "test.prisoner-offender-search.prisoner.updated",
        "test.prisoner-offender-search.prisoner.updated",
        "test.prisoner-offender-search.prisoner.released",
        "test.prisoner-offender-search.prisoner.alerts-updated",
      )
    }
  }

  @Nested
  inner class RefreshActiveIndex {
    @BeforeEach
    fun setUp() {
      prisonApi.stubActiveOffenders(
        PrisonerBuilder("A9999AA"),
        PrisonerBuilder("A7089EY", released = true, heightCentimetres = 200),
      )
    }

    @Test
    fun `Refresh active index - no differences`() {
      reset(telemetryClient)

      webTestClient.put().uri("/refresh-index/active")
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
        .exchange()
        .expectStatus().isAccepted

      await untilCallTo { indexSqsClient.countAllMessagesOnQueue(indexQueueUrl).get() } matches { it!! > 0 }
      await untilCallTo { indexSqsClient.countAllMessagesOnQueue(indexQueueUrl).get() } matches { it == 0 }

      verifyNoInteractions(telemetryClient)
    }

    @Test
    fun `Refresh active index - unauthorised if not correct role`() {
      webTestClient.put().uri("/refresh-index/active")
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_VIEW")))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `Reconciliation - differences`() {
      val eventCaptor = argumentCaptor<Map<String, String>>()
      val startOfTest = Instant.now()

      // Modify index record A9999AA a little
      prisonerRepository.get("A9999AA")!!.apply {
        releaseDate = LocalDate.parse("2023-01-02")
      }.also {
        prisonerRepository.save(it)
      }
      // Modify index record A7089EY a lot
      Prisoner().apply {
        prisonerNumber = "A7089EY"
        status = "ACTIVE IN"
        prisonId = "MDI"
        restrictedPatient = false
        complexityOfNeedLevel = "medium"
      }.also {
        prisonerRepository.save(it)
      }

      // A7089EY complexityOfNeed is changing from a value to null
      complexityOfNeedApi.stubNotFound("A7089EY")

      val detailsForA9999AA = webTestClient.get().uri("/compare-index/prisoner/A9999AA")
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
        .exchange()
        .expectStatus().isOk
        .expectBody<String>()
        .returnResult().responseBody

      assertThat(detailsForA9999AA).isEqualTo("""[[releaseDate: 2023-01-02, null]]""")

      alertsApi.stubSuccess("A9999AA")

      val detailsForA7089EY = webTestClient.get().uri("/compare-index/prisoner/A7089EY")
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
        .exchange()
        .expectStatus().isOk
        .expectBody<String>()
        .returnResult().responseBody

      assertThat(detailsForA7089EY).contains(
        "[active: true, false]",
        "[bookNumber: null, V61587]",
        "[croNumber: null, 29906/12L]",
        "[dateOfBirth: null, 1965-07-19]",
        "[complexityOfNeedLevel: medium, null]",
      )

      webTestClient.put().uri("/refresh-index/active")
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
      assertThat(differences["A9999AA"]).isEqualTo("[ALERTS, SENTENCE]")
      assertThat(differences["A7089EY"]).isEqualTo("[IDENTIFIERS, LOCATION, PERSONAL_DETAILS, PHYSICAL_DETAILS, STATUS]")

      webTestClient.get().uri("/prisoner-differences?from=$startOfTest")
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.[?(@.nomsNumber=='A9999AA')].differences").value<JSONArray> {
          assertThat(it.toList()).hasSize(1)
          assertThat(it.toList()[0].toString()).contains(
            "[releaseDate: 2023-01-02, null]",
            "[alerts: null, [PrisonerAlert(alertType=A, alertCode=ABC, active=true, expired=true)]]",
          )
        }
        .jsonPath("$.[?(@.nomsNumber=='A7089EY')].differences").value<JSONArray> {
          assertThat(it.toList()).hasSize(1)
          assertThat(it.toList()[0].toString()).contains(
            "[active: true, false]",
            "[bookNumber: null, V61587]",
            "[dateOfBirth: null, 1965-07-19]",
          )
        }

      await untilCallTo { getNumberOfMessagesCurrentlyOnDomainQueue() } matches { it == 4 }

      assertThat(
        listOf(
          readEventFromNextDomainEventMessage(),
          readEventFromNextDomainEventMessage(),
          readEventFromNextDomainEventMessage(),
          readEventFromNextDomainEventMessage(),
        ),
      ).containsExactlyInAnyOrder(
        "test.prisoner-offender-search.prisoner.updated",
        "test.prisoner-offender-search.prisoner.updated",
        "test.prisoner-offender-search.prisoner.released",
        "test.prisoner-offender-search.prisoner.alerts-updated",
      )
    }
  }
}
