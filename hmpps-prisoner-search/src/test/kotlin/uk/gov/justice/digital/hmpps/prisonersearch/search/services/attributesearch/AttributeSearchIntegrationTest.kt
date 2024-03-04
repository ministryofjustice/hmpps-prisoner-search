package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.SyncIndex
import uk.gov.justice.digital.hmpps.prisonersearch.search.AbstractSearchDataIntegrationTest
import uk.gov.justice.digital.hmpps.prisonersearch.search.model.IncentiveLevelBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.search.model.PrisonerBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.search.repository.PrisonerRepository
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.AttributeSearchRequest
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.JoinType.OR
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.requestdsl.CONTAINS
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.requestdsl.IS
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.requestdsl.IS_NOT
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.requestdsl.RequestDsl

/**
 * Test the attribute search engine.
 *
 * Note that this class builds AttributeSearchRequest instances using the [RequestDsl]. If you want to know how the requests
 * look in JSON format see test class [AttributeSearchRequestJsonTest].
 */
class AttributeSearchIntegrationTest : AbstractSearchDataIntegrationTest() {

  @Autowired
  private lateinit var objectMapper: ObjectMapper

  private val prisonerBuilders = mutableListOf<PrisonerBuilder>()

  // The super class refreshes the index before this test class runs, but we don't want it to load any prisoners yet
  override fun loadPrisonerData() {}

  @BeforeEach
  fun `clear search repository`() {
    prisonerBuilders.onEach { prisonerRepository.delete(it.prisonerNumber) }.clear()
    waitForPrisonerLoading(expectedCount = 0)
  }

  @Nested
  inner class StringMatchers {
    @Test
    fun `single IS`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", firstName = "John"),
        PrisonerBuilder(prisonerNumber = "B1234BB", firstName = "Jeff"),
        PrisonerBuilder(prisonerNumber = "C1234CC", firstName = "Johnson"),
      )

      val request = RequestDsl {
        query {
          stringMatcher("firstName" IS "John")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("A1234AA")
    }

    @Test
    fun `single IS on an attribute without a keyword defined`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", cellLocation = "C-1-2"),
        PrisonerBuilder(prisonerNumber = "B1234BB", cellLocation = "D-1-3"),
        PrisonerBuilder(prisonerNumber = "C1234CC", cellLocation = "C-1-2-3"),
      )

      val request = RequestDsl {
        query {
          stringMatcher("cellLocation" IS "C-1-2")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("A1234AA")
    }

    @Test
    fun `IS with AND`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", firstName = "John", lastName = "Smith"),
        PrisonerBuilder(prisonerNumber = "B1234BB", firstName = "John", lastName = "Jones"),
        PrisonerBuilder(prisonerNumber = "C1234CC", firstName = "Jeff", lastName = "Smith"),
      )

      val request = RequestDsl {
        query {
          stringMatcher("firstName" IS "John")
          stringMatcher("lastName" IS "Smith")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("A1234AA")
    }

    @Test
    fun `IS with OR`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", firstName = "John", lastName = "Smith"),
        PrisonerBuilder(prisonerNumber = "B1234BB", firstName = "Jeff", lastName = "Jones"),
        PrisonerBuilder(prisonerNumber = "C1234CC", firstName = "Jimmy", lastName = "Power"),
      )

      val request = RequestDsl {
        query {
          joinType = OR
          stringMatcher("firstName" IS "John")
          stringMatcher("lastName" IS "Jones")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("A1234AA", "B1234BB")
    }

    @Test
    fun `single IS_NOT`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", lastName = "Smith"),
        PrisonerBuilder(prisonerNumber = "B1234BB", lastName = "Jones"),
      )

      val request = RequestDsl {
        query {
          stringMatcher("lastName" IS_NOT "Jones")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("A1234AA")
    }

    @Test
    fun `single IS_NOT on an attribute without a keyword defined`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", cellLocation = "C-1-2"),
        PrisonerBuilder(prisonerNumber = "B1234BB", cellLocation = "D-1-3"),
        PrisonerBuilder(prisonerNumber = "C1234CC", cellLocation = "C-1-2-3"),
      )

      val request = RequestDsl {
        query {
          stringMatcher("cellLocation" IS_NOT "C-1-2")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("B1234BB", "C1234CC")
    }

    @Test
    fun `IS_NOT with AND`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", lastName = "Smith"),
        PrisonerBuilder(prisonerNumber = "B1234BB", lastName = "Jones"),
        PrisonerBuilder(prisonerNumber = "C1234CC", lastName = "Power"),
      )

      val request = RequestDsl {
        query {
          stringMatcher("lastName" IS_NOT "Jones")
          stringMatcher("lastName" IS_NOT "Power")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("A1234AA")
    }

    @Test
    fun `IS_NOT with OR`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", firstName = "John", lastName = "Smith"),
        PrisonerBuilder(prisonerNumber = "B1234BB", firstName = "John", lastName = "Jones"),
        PrisonerBuilder(prisonerNumber = "C1234CC", firstName = "Jeff", lastName = "Jones"),
      )

      val request = RequestDsl {
        query {
          joinType = OR
          stringMatcher("firstName" IS_NOT "John")
          stringMatcher("lastName" IS_NOT "Jones")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("A1234AA", "C1234CC")
    }

    @Test
    fun `single CONTAINS`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", lastName = "Smith"),
        PrisonerBuilder(prisonerNumber = "B1234BB", lastName = "Jones"),
        PrisonerBuilder(prisonerNumber = "C1234CC", lastName = "Taylor-Smith"),
      )

      val request = RequestDsl {
        query {
          stringMatcher("lastName" CONTAINS "Smith")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("A1234AA", "C1234CC")
    }

    @Test
    fun `single CONTAINS with partial matches in search term`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", cellLocation = "C-1-2"),
        PrisonerBuilder(prisonerNumber = "B1234BB", cellLocation = "D-1-2"),
        PrisonerBuilder(prisonerNumber = "C1234CC", cellLocation = "C-1-2-3"),
      )

      // The "-1-2" matches all prisoners, so we're testing that the results must contain the whole search term
      val request = RequestDsl {
        query {
          stringMatcher("cellLocation" CONTAINS "C-1-2")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("A1234AA", "C1234CC")
    }

    @Test
    fun `CONTAINS with AND`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", lastName = "Smith"),
        PrisonerBuilder(prisonerNumber = "B1234BB", lastName = "Jones"),
        PrisonerBuilder(prisonerNumber = "C1234CC", lastName = "Taylor-Smith"),
      )

      val request = RequestDsl {
        query {
          stringMatcher("lastName" CONTAINS "Taylor")
          stringMatcher("lastName" CONTAINS "Smith")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("C1234CC")
    }

    @Test
    fun `CONTAINS with OR`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", lastName = "Taylor-Smith"),
        PrisonerBuilder(prisonerNumber = "B1234BB", lastName = "Taylor-Jones"),
        PrisonerBuilder(prisonerNumber = "C1234CC", lastName = "Taylor"),
      )

      val request = RequestDsl {
        query {
          joinType = OR
          stringMatcher("lastName" CONTAINS "Smith")
          stringMatcher("lastName" CONTAINS "Jones")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("A1234AA", "B1234BB")
    }

    @Test
    fun `IS, CONTAINS and IS_NOT`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", firstName = "John", lastName = "Taylor-Smith"),
        PrisonerBuilder(prisonerNumber = "B1234BB", firstName = "John", lastName = "Taylor-Jones"),
        PrisonerBuilder(prisonerNumber = "C1234CC", firstName = "Jack", lastName = "Taylor-Smith"),
      )

      val request = RequestDsl {
        query {
          stringMatcher("firstName" IS "John")
          stringMatcher("lastName" CONTAINS "Taylor")
          stringMatcher("lastName" IS_NOT "Taylor-Jones")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("A1234AA")
    }

    @Test
    fun `IS, CONTAINS and IS_NOT with OR`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", firstName = "John", lastName = "Smith"),
        PrisonerBuilder(prisonerNumber = "B1234BB", firstName = "Jack", lastName = "Taylor-Jones"),
        PrisonerBuilder(prisonerNumber = "C1234CC", firstName = "Jack", lastName = "Taylor-Smith"),
        PrisonerBuilder(prisonerNumber = "D1234DD", firstName = "Jack", lastName = "Smith"),
      )

      val request = RequestDsl {
        query {
          joinType = OR
          stringMatcher("firstName" IS_NOT "Jack")
          stringMatcher("lastName" CONTAINS "Jones")
          stringMatcher("lastName" IS "Taylor-Smith")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("A1234AA", "B1234BB", "C1234CC")
    }

    @Test
    fun `IS with field from an array`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", alertCodes = listOf("P" to "P11", "O" to "OPPO")),
        PrisonerBuilder(prisonerNumber = "B1234BB", alertCodes = listOf("X" to "XSA", "O" to "OPPO")),
        PrisonerBuilder(prisonerNumber = "C1234CC", alertCodes = listOf("X" to "XSA", "V" to "V45")),
      )

      val request = RequestDsl {
        query {
          stringMatcher("alerts.alertType" IS "O")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("A1234AA", "B1234BB")
    }

    @Test
    fun `IS with AND fields from an array`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", alertCodes = listOf("P" to "P11", "O" to "OPPO")),
        PrisonerBuilder(prisonerNumber = "B1234BB", alertCodes = listOf("X" to "XSA", "O" to "OPPO")),
        PrisonerBuilder(prisonerNumber = "C1234CC", alertCodes = listOf("X" to "XSA", "V" to "V45")),
      )

      val request = RequestDsl {
        query {
          stringMatcher("alerts.alertType" IS "O")
          stringMatcher("alerts.alertType" IS "X")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("B1234BB")
    }

    @Test
    fun `IS with OR fields from an array`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", alertCodes = listOf("P" to "P11", "O" to "OPPO")),
        PrisonerBuilder(prisonerNumber = "B1234BB", alertCodes = listOf("X" to "XSA", "O" to "OPPO")),
        PrisonerBuilder(prisonerNumber = "C1234CC", alertCodes = listOf("X" to "XSA", "V" to "V45")),
      )

      val request = RequestDsl {
        query {
          joinType = OR
          stringMatcher("alerts.alertCode" IS "P11")
          stringMatcher("alerts.alertCode" IS "V45")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("A1234AA", "C1234CC")
    }

    @Test
    fun `IS with field from a nested object`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", currentIncentive = IncentiveLevelBuilder("STD")),
        PrisonerBuilder(prisonerNumber = "B1234BB", currentIncentive = IncentiveLevelBuilder("ENH")),
      )

      val request = RequestDsl {
        query {
          stringMatcher("currentIncentive.level.code" IS "STD")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("A1234AA")
    }

    @Test
    fun `IS with AND fields from a nested object`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", currentIncentive = IncentiveLevelBuilder("STD")),
        PrisonerBuilder(prisonerNumber = "B1234BB", currentIncentive = IncentiveLevelBuilder("ENH")),
      )

      // This doesn't make sense - incentive cannot be both STD and ENH - but just want to make sure it returns nothing
      val request = RequestDsl {
        query {
          stringMatcher("currentIncentive.level.code" IS "STD")
          stringMatcher("currentIncentive.level.code" IS "ENH")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners()
    }

    @Test
    fun `IS_NOT with field from an array`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", alertCodes = listOf("P" to "P11", "O" to "OPPO")),
        PrisonerBuilder(prisonerNumber = "B1234BB", alertCodes = listOf("X" to "XSA", "O" to "OPPO")),
        PrisonerBuilder(prisonerNumber = "C1234CC", alertCodes = listOf("X" to "XSA", "V" to "V45")),
      )

      val request = RequestDsl {
        query {
          stringMatcher("alerts.alertCode" IS_NOT "OPPO")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("C1234CC")
    }

    @Test
    fun `IS_NOT with AND fields from an array`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", alertCodes = listOf("P" to "P11", "O" to "OPPO")),
        PrisonerBuilder(prisonerNumber = "B1234BB", alertCodes = listOf("X" to "XSA", "O" to "OPPO")),
        PrisonerBuilder(prisonerNumber = "C1234CC", alertCodes = listOf("X" to "XSA", "V" to "V45")),
      )

      val request = RequestDsl {
        query {
          stringMatcher("alerts.alertCode" IS_NOT "OPPO")
          stringMatcher("alerts.alertCode" IS_NOT "P11")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("C1234CC")
    }

    @Test
    fun `IS_NOT with OR fields from an array`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", alertCodes = listOf("P" to "P11")),
        PrisonerBuilder(prisonerNumber = "B1234BB", alertCodes = listOf("O" to "OPPO")),
        PrisonerBuilder(prisonerNumber = "C1234CC", alertCodes = listOf("P" to "P11", "O" to "OPPO", "V" to "V45")),
      )

      val request = RequestDsl {
        query {
          joinType = OR
          stringMatcher("alerts.alertCode" IS_NOT "OPPO")
          stringMatcher("alerts.alertCode" IS_NOT "P11")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("A1234AA", "B1234BB")
    }

    @Test
    fun `IS_NOT with field from a nested object`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", currentIncentive = IncentiveLevelBuilder("STD")),
        PrisonerBuilder(prisonerNumber = "B1234BB", currentIncentive = IncentiveLevelBuilder("ENH")),
      )

      val request = RequestDsl {
        query {
          stringMatcher("currentIncentive.level.code" IS_NOT "ENH")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("A1234AA")
    }

    @Test
    fun `CONTAINS with field from an array`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", alertCodes = listOf("C" to "CC1", "P" to "P11")),
        PrisonerBuilder(prisonerNumber = "B1234BB", alertCodes = listOf("C" to "CC2", "O" to "OPPO")),
        PrisonerBuilder(prisonerNumber = "C1234CC", alertCodes = listOf("X" to "CA", "P" to "P11")),
      )

      val request = RequestDsl {
        query {
          stringMatcher("alerts.alertCode" CONTAINS "CC")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("A1234AA", "B1234BB")
    }

    @Test
    fun `CONTAINS with AND fields from an array`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", alertCodes = listOf("C" to "CC1", "P" to "P11")),
        PrisonerBuilder(prisonerNumber = "B1234BB", alertCodes = listOf("C" to "CC2", "O" to "OPPO")),
        PrisonerBuilder(prisonerNumber = "C1234CC", alertCodes = listOf("X" to "CA", "P" to "P11")),
      )

      val request = RequestDsl {
        query {
          stringMatcher("alerts.alertCode" CONTAINS "CC")
          stringMatcher("alerts.alertCode" CONTAINS "OPP")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("B1234BB")
    }

    @Test
    fun `CONTAINS with OR fields from an array`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", alertCodes = listOf("C" to "CC1", "P" to "P11")),
        PrisonerBuilder(prisonerNumber = "B1234BB", alertCodes = listOf("X" to "CA", "O" to "OPPO")),
        PrisonerBuilder(prisonerNumber = "C1234CC", alertCodes = listOf("X" to "CA", "P" to "P11")),
      )

      val request = RequestDsl {
        query {
          joinType = OR
          stringMatcher("alerts.alertCode" CONTAINS "CC")
          stringMatcher("alerts.alertCode" CONTAINS "OPP")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("A1234AA", "B1234BB")
    }

    @Test
    fun `CONTAINS with field from a nested object`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", currentIncentive = IncentiveLevelBuilder("STD", "Standard")),
        PrisonerBuilder(prisonerNumber = "B1234BB", currentIncentive = IncentiveLevelBuilder("ENH", "Enhanced")),
        PrisonerBuilder(prisonerNumber = "C1234CC", currentIncentive = IncentiveLevelBuilder("EN2", "Enhanced 2")),
      )

      val request = RequestDsl {
        query {
          stringMatcher("currentIncentive.level.description" CONTAINS "Enhanced")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("B1234BB", "C1234CC")
    }
  }

  private fun PrisonerRepository.delete(prisonerNumber: String) = delete(prisonerNumber, SyncIndex.GREEN)

  private fun loadPrisoners(vararg prisoners: PrisonerBuilder) =
    prisonerBuilders.apply {
      addAll(prisoners)
      loadPrisonersFromBuilders(this)
    }

  private fun WebTestClient.attributeSearch(request: AttributeSearchRequest) =
    post()
      .uri("/attribute-search")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_SEARCH")))
      .header("Content-Type", "application/json")
      .bodyValue(objectMapper.writeValueAsString(request))
      .exchange()
      .expectStatus().isOk

  private fun WebTestClient.ResponseSpec.expectPrisoners(vararg prisonerNumbers: String) =
    expectBody()
      .jsonPath("$.content[*].prisonerNumber").value<List<String>> {
        assertThat(it).containsExactlyInAnyOrderElementsOf(listOf(*prisonerNumbers))
      }
}
