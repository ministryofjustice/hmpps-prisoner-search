@file:Suppress("ClassName")

package uk.gov.justice.digital.hmpps.prisonersearch.indexer.resource

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.SyncIndex
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.AliasBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.PrisonerBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.wiremock.PrisonApiExtension.Companion.prisonApi

class CompareIndexResourceIntTest : IntegrationTestBase() {
  @SpyBean
  lateinit var telemetryClient: TelemetryClient

  @Nested
  inner class compareTestsNoData {
    @BeforeEach
    fun beforeEach() = prisonApi.stubOffenders()

    @Test
    @DisplayName("/compare-index/size endpoint is unsecured")
    fun `size endpoint is unsecured`() {
      webTestClient.get().uri("/compare-index/size")
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
    }

    @Test
    @DisplayName("/compare-index/ids access forbidden when no authority")
    fun `ids endpoint forbidden when no authority`() {
      webTestClient.get().uri("/compare-index/ids")
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    @DisplayName("/compare-index/ids access forbidden when no role")
    fun `ids endpoint access forbidden when no role`() {
      webTestClient.get().uri("/compare-index/ids")
        .headers(setAuthorisation())
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    @DisplayName("/compare-index/prisoner/{prisonerNumber} access forbidden when no authority")
    fun `prisoner endpoint forbidden when no authority`() {
      webTestClient.get().uri("/compare-index/prisoner/A1234BC")
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    @DisplayName("/compare-index/ids access forbidden when no role")
    fun `prisoner endpoint access forbidden when no role`() {
      webTestClient.get().uri("/compare-index/prisoner/A1234BC")
        .headers(setAuthorisation())
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isForbidden
    }
  }

  @Nested
  inner class compareTestsWithDifferences {
    @BeforeEach
    fun beforeEach() {
      prisonApi.stubOffenders(
        PrisonerBuilder("A9999AA"),
        PrisonerBuilder("A9999AB"),
        PrisonerBuilder("A9999AC"),
        PrisonerBuilder("A9999RA"),
        PrisonerBuilder("A9999RB"),
        PrisonerBuilder("A9999RC"),
        PrisonerBuilder("A7089EY"),
        PrisonerBuilder("A7089EZ"),
        PrisonerBuilder("A7089FA"),
      )
      buildAndSwitchIndex(SyncIndex.GREEN, 9)

      prisonApi.stubOffenders(
        PrisonerBuilder("A9999AA"),
        PrisonerBuilder("A9999AB"),
        PrisonerBuilder("A9999AC"),
        PrisonerBuilder("A9999RA"),
        PrisonerBuilder("A9999RB"),
        PrisonerBuilder("A1234SR"),
      )
    }

    @Test
    @DisplayName("/compare-index/ids telemetry is recorded")
    fun `ids endpoint telemetry is recorded`() {
      webTestClient.get().uri("/compare-index/ids")
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
        .exchange()
        .expectStatus().isAccepted

      verify(telemetryClient, timeout(2000).atLeastOnce()).trackEvent(
        eq("COMPARE_INDEX_IDS"),
        check<Map<String, String>> {
          assertThat(it["onlyInIndex"]).isEqualTo("[A7089EY, A7089EZ, A7089FA, A9999RC]")
          assertThat(it["onlyInNomis"]).isEqualTo("[A1234SR]")
          assertThat(it["timeMs"]?.toInt()).isGreaterThan(0)
        },
        isNull(),
      )
    }

    @Test
    @DisplayName("/compare-index/size telemetry is recorded")
    fun `size endpoint telemetry is recorded`() {
      webTestClient.get().uri("/compare-index/size")
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk

      verify(telemetryClient, atLeastOnce()).trackEvent(
        eq("COMPARE_INDEX_SIZE"),
        check<Map<String, String>> {
          assertThat(it["timeMs"]?.toInt()).isGreaterThan(0)
          assertThat(it["totalNomis"]?.toInt()).isEqualTo(6)
          assertThat(it["totalIndex"]?.toInt()).isEqualTo(9)
        },
        isNull(),
      )
    }

    @Test
    @DisplayName("/compare-index/size returns correct data")
    fun `size endpoint returns differences`() {
      webTestClient.get().uri("/compare-index/size")
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("timeMs").value<Int?> { assertThat(it).isGreaterThan(0) }
        .jsonPath("totalNomis").value<Int?> { assertThat(it).isEqualTo(6) }
        .jsonPath("totalIndex").value<Int?> { assertThat(it).isEqualTo(9) }
    }

    @Test
    @DisplayName("/compare-index/prisoner/{prisonerNumber} returns differences")
    fun `prisoner endpoint returns differences`() {
      prisonApi.stubGetOffender(
        PrisonerBuilder(
          "A9999AA",
          alertCodes = listOf("P" to "PL1"),
          aliases = listOf(
            AliasBuilder(gender = "F"),
          ),
        ),
      )

      val details = webTestClient.get().uri("/compare-index/prisoner/A9999AA")
        .header("Content-Type", "application/json")
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
        .exchange()
        .expectStatus().isOk
        .expectBody<String>()
        .returnResult().responseBody

      assertThat(details).contains(
        "[alerts: [], [PrisonerAlert(alertType=P, alertCode=PL1, active=true, expired=false)]",
        "[aliases: [], [PrisonerAlias(firstName=LUCAS, middleNames=null, lastName=MORALES, dateOfBirth=1965-07-19, gender=F, ethnicity=null)]]",
      )
    }
  }
}
