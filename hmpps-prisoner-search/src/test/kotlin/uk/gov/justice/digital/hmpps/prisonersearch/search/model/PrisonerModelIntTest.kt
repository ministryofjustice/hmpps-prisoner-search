package uk.gov.justice.digital.hmpps.prisonersearch.search.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import uk.gov.justice.digital.hmpps.prisonersearch.search.AbstractSearchIntegrationTest
import java.time.LocalDate

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PrisonerModelIntTest : AbstractSearchIntegrationTest() {

  private val prisoner = PrisonerBuilder(
    prisonerNumber = "A1111AA",
    addresses = listOf(
      AddressBuilder(
        flat = "1",
        premise = "2",
        street = "High Street",
        locality = "Crookes",
        town = "Sheffield",
        postalCode = "S12 3DE",
        county = "South Yorkshire",
        country = "England",
        startDate = LocalDate.parse("2013-12-02"),
        primary = true,
      ),
    ),
  )

  override fun loadPrisonerData() {
    loadPrisonersFromBuilders(listOf(prisoner))
  }

  @Test
  fun `should save and retrieve address data`() {
    webTestClient.get().uri("/prisoner/A1111AA")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("addresses[0].fullAddress").isEqualTo("Flat 1, 2 High Street, Crookes, Sheffield, South Yorkshire, S12 3DE, England")
      .jsonPath("addresses[0].postalCode").isEqualTo("S12 3DE")
      .jsonPath("addresses[0].primaryAddress").isEqualTo(true)
      .jsonPath("addresses[0].startDate").isEqualTo("2013-12-02")
  }
}
