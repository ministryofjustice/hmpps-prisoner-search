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
    bookingId = 2,
    prisonerNumber = "A1111AA",
    title = "Mr",
    ethnicity = "Mixed White Black",
    raceCode = "M1",
    addresses = listOf(
      AddressBuilder(
        fullAddress = "Some full address",
        postalCode = "S12 3DE",
        startDate = LocalDate.parse("2013-12-02"),
        primary = true,
        phones = listOf(
          PhoneBuilder("MOB", "07987654321"),
          PhoneBuilder("HOME", "011987654321"),
        ),
      ),
    ),
    aliases = listOf(AliasBuilder(title = "Ms", raceCode = "W1", ethnicity = "White")),
    emailAddresses = listOf(
      EmailAddressBuilder("john.smith@gmail.com"),
      EmailAddressBuilder("john.smith@hotmail.com"),
    ),
    phones = listOf(
      PhoneBuilder("MOB", "07123 456789"),
      PhoneBuilder("HOME", "01123456789"),
    ),
    identifiers = listOf(
      IdentifierBuilder("PNC", "12/0394773H", "2019-07-17", "NOMIS", "2019-07-17T12:34:56.833133"),
      IdentifierBuilder("PNC", "12/0394773H", "2019-07-17", null, "2020-07-17T12:34:56.833133"),
      IdentifierBuilder("CRO", "145845/12U", null, "Incorrect CRO - typo", "2021-10-18T12:34:56.833133"),
      IdentifierBuilder("CRO", "145835/12U", null, null, "2021-10-19T12:34:56.833133"),
      IdentifierBuilder("NINO", "JE460605B", null, null, "2019-06-11T12:34:56.833133"),
      IdentifierBuilder("DL", "COLBO/912052/JM9MU", null, null, "2022-04-12T12:34:56.833133"),
    ),
    allConvictedOffences = listOf(
      OffenceBuilder(
        statuteCode = "TH68",
        offenceCode = "TH68012",
        offenceDescription = "Theft",
        offenceDate = LocalDate.parse("2020-01-01"),
        bookingId = 1,
        mostSerious = true,
        offenceSeverityRanking = 100,
        sentenceStartDate = null,
        primarySentence = null,
      ),
      OffenceBuilder(
        statuteCode = "TH68",
        offenceCode = "TH68057",
        offenceDescription = "Robbery",
        offenceDate = LocalDate.parse("2024-02-01"),
        bookingId = 2,
        mostSerious = true,
        offenceSeverityRanking = 100,
        sentenceStartDate = LocalDate.parse("2017-02-03"),
        primarySentence = true,
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
      .jsonPath("addresses[0].fullAddress").isEqualTo("Some full address")
      .jsonPath("addresses[0].postalCode").isEqualTo("S12 3DE")
      .jsonPath("addresses[0].primaryAddress").isEqualTo(true)
      .jsonPath("addresses[0].noFixedAddress").isEqualTo(false)
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
  fun `should save and retrieve ethnicity`() {
    webTestClient.get().uri("/prisoner/A1111AA")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("aliases[0].raceCode").isEqualTo("W1")
      .jsonPath("aliases[0].ethnicity").isEqualTo("White")
      .jsonPath("ethnicity").isEqualTo("Mixed White Black")
      .jsonPath("raceCode").isEqualTo("M1")
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
            .containsExactlyInAnyOrder(
              Identifier("NINO", "JE460605B", null, null, LocalDateTime.parse("2019-06-11T12:34:56")),
              Identifier("PNC", "12/0394773H", LocalDate.parse("2019-07-17"), "NOMIS", LocalDateTime.parse("2019-07-17T12:34:56")),
              Identifier("PNC", "12/0394773H", LocalDate.parse("2019-07-17"), null, LocalDateTime.parse("2020-07-17T12:34:56")),
              Identifier("CRO", "145845/12U", null, "Incorrect CRO - typo", LocalDateTime.parse("2021-10-18T12:34:56")),
              Identifier("CRO", "145835/12U", null, null, LocalDateTime.parse("2021-10-19T12:34:56")),
              Identifier("DL", "COLBO/912052/JM9MU", null, null, LocalDateTime.parse("2022-04-12T12:34:56")),
            )
          assertThat(pncNumber).isEqualTo("12/0394773H")
          assertThat(pncNumberCanonicalShort).isEqualTo("12/394773H")
          assertThat(pncNumberCanonicalLong).isEqualTo("2012/394773H")
          assertThat(croNumber).isEqualTo("145845/12U")
        }
      }
  }

  @Test
  fun `should save and retrieve convicted offences`() {
    webTestClient.get().uri("/prisoner/A1111AA")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("allConvictedOffences").value<List<LinkedHashMap<String, Any>>> { l ->
        assertThat(l.map { listOf(it["statuteCode"], it["offenceCode"], it["offenceDescription"], it["offenceDate"], it["latestBooking"], it["sentenceStartDate"], it["primarySentence"]) })
          .containsExactlyInAnyOrder(
            listOf("TH68", "TH68012", "Theft", "2020-01-01", false, null, null),
            listOf("TH68", "TH68057", "Robbery", "2024-02-01", true, "2017-02-03", true),
          )
      }
  }
}
