@file:Suppress("ClassName")

package uk.gov.justice.digital.hmpps.prisonersearchindexer.integration.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisonersearchindexer.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonersearchindexer.integration.PrisonerBuilder
import uk.gov.justice.digital.hmpps.prisonersearchindexer.integration.wiremock.PrisonApiExtension.Companion.prisonApi
import uk.gov.justice.digital.hmpps.prisonersearchindexer.model.SyncIndex
import uk.gov.justice.digital.hmpps.prisonersearchindexer.repository.PrisonerRepository

class IndexResource_compareIndexTest : IntegrationTestBase() {

  @Autowired
  lateinit var prisonerRepository: PrisonerRepository

  @Test
  fun `access forbidden when no authority`() {
    webTestClient.get().uri("/prisoner-index/compare-index")
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  fun `access forbidden when no role`() {
    webTestClient.get().uri("/prisoner-index/compare-index")
      .headers(setAuthorisation())
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `Diffs reported`() {
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

    webTestClient.get().uri("/prisoner-index/compare-index")
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
}
