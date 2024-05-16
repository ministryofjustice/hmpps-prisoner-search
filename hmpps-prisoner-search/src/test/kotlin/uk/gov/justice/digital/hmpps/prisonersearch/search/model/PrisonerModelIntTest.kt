package uk.gov.justice.digital.hmpps.prisonersearch.search.model

import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat
import org.assertj.core.groups.Tuple.tuple
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Identifier
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.PhoneNumber
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.search.AbstractSearchIntegrationTest
import java.time.LocalDate
import java.time.LocalDateTime

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PrisonerModelIntTest : AbstractSearchIntegrationTest() {

  private val prisoner = PrisonerBuilder(
    prisonerNumber = "A1111AA",
    title = "Mr",
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
        phones = listOf(
          PhoneBuilder("MOB", "07987 654321"),
          PhoneBuilder("HOME", "0119 87654321"),
          PhoneBuilder("OTH", "0111 111 111"),
        ),
      ),
    ),
    aliases = listOf(AliasBuilder(title = "Ms")),
    emailAddresses = listOf(
      EmailAddressBuilder("john.smith@gmail.com"),
      EmailAddressBuilder("john.smith@hotmail.com"),
    ),
    phones = listOf(
      PhoneBuilder("MOB", "07123 456789"),
      PhoneBuilder("HOME", "01123456789"),
    ),
    identifiers = listOf(
      IdentifierBuilder("PNC", "2012/0394773H", "2019-07-17", "NOMIS", "2019-07-17T12:34:56.833133"),
      IdentifierBuilder("PNC", "12/394773H", "2019-07-17", null, "2020-07-17T12:34:56.833133"),
      IdentifierBuilder("CRO", "145845/12U", null, "Incorrect CRO - typo", "2021-10-18T12:34:56.833133"),
      IdentifierBuilder("CRO", "145835/12U", null, null, "2021-10-19T12:34:56.833133"),
      IdentifierBuilder("NINO", "JE460605B", null, null, "2019-06-11T12:34:56.833133"),
      IdentifierBuilder("DL", "COLBO/912052/JM9MU", null, null, "2022-04-12T12:34:56.833133"),
      IdentifierBuilder("HOREF", "T3037620", null, null, "2020-04-12T12:34:56.833133"),
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

  @Test
  fun `should save and retrieve title`() {
    webTestClient.get().uri("/prisoner/A1111AA")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("title").isEqualTo("Mr")
      .jsonPath("aliases[0].title").isEqualTo("Ms")
  }

  @Test
  fun `should save and retrieve email addresses`() {
    webTestClient.get().uri("/prisoner/A1111AA")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("emailAddresses[*].email").value<List<String>> {
        assertThat(it).containsExactlyInAnyOrder("john.smith@gmail.com", "john.smith@hotmail.com")
      }
  }

  @Test
  fun `should save and retrieve telephone numbers`() {
    webTestClient.get().uri("/prisoner/A1111AA")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("phoneNumbers").value<List<PhoneNumber>> {
        assertThat(it).extracting("type", "number").containsExactlyInAnyOrder(
          tuple("MOB", "07123456789"),
          tuple("HOME", "01123456789"),
        )
      }
  }

  @Test
  fun `should save and retrieve address telephone numbers`() {
    webTestClient.get().uri("/prisoner/A1111AA")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("addresses[0].phoneNumbers").value<List<PhoneNumber>> {
        assertThat(it).extracting("type", "number").containsExactlyInAnyOrder(
          tuple("MOB", "07987654321"),
          tuple("HOME", "011987654321"),
        )
      }
  }

  @Test
  fun `should save and retrieve identifiers`() {
    webTestClient.get().uri("/prisoner/A1111AA")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
      .expectBody<Prisoner>()
      .consumeWith {
        with(it.responseBody as Prisoner) {
          assertThat(identifiers)
            .containsExactly(
              Identifier("NINO", "JE460605B", null, null, LocalDateTime.parse("2019-06-11T12:34:56")),
              Identifier("PNC", "12/394773H", LocalDate.parse("2019-07-17"), "NOMIS", LocalDateTime.parse("2019-07-17T12:34:56")),
              Identifier("PNC", "12/394773H", LocalDate.parse("2019-07-17"), null, LocalDateTime.parse("2020-07-17T12:34:56")),
              Identifier("CRO", "145845/12U", null, "Incorrect CRO - typo", LocalDateTime.parse("2021-10-18T12:34:56")),
              Identifier("CRO", "145835/12U", null, null, LocalDateTime.parse("2021-10-19T12:34:56")),
              Identifier("DL", "COLBO/912052/JM9MU", null, null, LocalDateTime.parse("2022-04-12T12:34:56")),
            )
        }
      }
  }
}
