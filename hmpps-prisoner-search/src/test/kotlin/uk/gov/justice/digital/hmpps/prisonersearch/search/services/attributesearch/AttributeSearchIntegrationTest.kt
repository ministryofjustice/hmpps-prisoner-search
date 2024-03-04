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
import uk.gov.justice.digital.hmpps.prisonersearch.search.model.PrisonerBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.search.repository.PrisonerRepository
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.AttributeSearchRequest
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.JoinType.OR
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.requestdsl.RequestDsl
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.requestdsl.has
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.requestdsl.`is`
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.requestdsl.isNot

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
          stringMatcher("firstName" `is` "John")
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
          stringMatcher("cellLocation" `is` "C-1-2")
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
          stringMatcher("firstName" `is` "John")
          stringMatcher("lastName" `is` "Smith")
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
          stringMatcher("firstName" `is` "John")
          stringMatcher("lastName" `is` "Jones")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("A1234AA", "B1234BB")
    }

    @Test
    fun `single IS_NOT`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", firstName = "John", lastName = "Smith"),
        PrisonerBuilder(prisonerNumber = "B1234BB", firstName = "John", lastName = "Jones"),
      )

      val request = RequestDsl {
        query {
          stringMatcher("lastName" isNot "Jones")
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
          stringMatcher("cellLocation" isNot "C-1-2")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("B1234BB", "C1234CC")
    }

    @Test
    fun `IS_NOT with AND`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", firstName = "John", lastName = "Smith"),
        PrisonerBuilder(prisonerNumber = "B1234BB", firstName = "John", lastName = "Jones"),
        PrisonerBuilder(prisonerNumber = "C1234CC", firstName = "John", lastName = "Power"),
      )

      val request = RequestDsl {
        query {
          stringMatcher("lastName" isNot "Jones")
          stringMatcher("lastName" isNot "Power")
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
          stringMatcher("firstName" isNot "John")
          stringMatcher("lastName" isNot "Jones")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("A1234AA", "C1234CC")
    }

    @Test
    fun `single CONTAINS`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", firstName = "John", lastName = "Smith"),
        PrisonerBuilder(prisonerNumber = "B1234BB", firstName = "John", lastName = "Jones"),
        PrisonerBuilder(prisonerNumber = "C1234CC", firstName = "John", lastName = "Taylor-Smith"),
      )

      val request = RequestDsl {
        query {
          stringMatcher("lastName" has "Smith")
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
          stringMatcher("cellLocation" has "C-1-2")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("A1234AA", "C1234CC")
    }

    @Test
    fun `CONTAINS with AND`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", firstName = "John", lastName = "Smith"),
        PrisonerBuilder(prisonerNumber = "B1234BB", firstName = "John", lastName = "Jones"),
        PrisonerBuilder(prisonerNumber = "C1234CC", firstName = "John", lastName = "Taylor-Smith"),
      )

      val request = RequestDsl {
        query {
          stringMatcher("lastName" has "Taylor")
          stringMatcher("lastName" has "Smith")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("C1234CC")
    }

    @Test
    fun `CONTAINS with OR`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", firstName = "John", lastName = "Taylor-Smith"),
        PrisonerBuilder(prisonerNumber = "B1234BB", firstName = "John", lastName = "Taylor-Jones"),
        PrisonerBuilder(prisonerNumber = "C1234CC", firstName = "John", lastName = "Taylor"),
      )

      val request = RequestDsl {
        query {
          joinType = OR
          stringMatcher("lastName" has "Smith")
          stringMatcher("lastName" has "Jones")
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
          stringMatcher("firstName" `is` "John")
          stringMatcher("lastName" has "Taylor")
          stringMatcher("lastName" isNot "Taylor-Jones")
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
          stringMatcher("firstName" isNot "Jack")
          stringMatcher("lastName" has "Jones")
          stringMatcher("lastName" `is` "Taylor-Smith")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("A1234AA", "B1234BB", "C1234CC")
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
