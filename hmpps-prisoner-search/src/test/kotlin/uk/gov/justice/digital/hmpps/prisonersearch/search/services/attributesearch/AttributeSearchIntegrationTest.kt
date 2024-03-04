package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
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
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.requestdsl.`is`

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

  @Test
  fun `single string matcher`() {
    loadPrisoners(
      PrisonerBuilder(prisonerNumber = "A1234AA", firstName = "John"),
      PrisonerBuilder(prisonerNumber = "B1234BB", firstName = "Jeff"),
    )

    val request = RequestDsl {
      query {
        stringMatcher("firstName" `is` "John")
      }
    }

    webTestClient.attributeSearch(request).expectPrisoners("A1234AA")
  }

  @Test
  fun `AND string matchers`() {
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
  fun `OR string matchers`() {
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
        assertThat(it).containsExactlyElementsOf(listOf(*prisonerNumbers))
      }
}
