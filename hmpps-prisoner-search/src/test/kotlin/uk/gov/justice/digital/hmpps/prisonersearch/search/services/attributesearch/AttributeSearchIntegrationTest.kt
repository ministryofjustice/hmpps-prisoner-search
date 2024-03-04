package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.SyncIndex
import uk.gov.justice.digital.hmpps.prisonersearch.search.AbstractSearchDataIntegrationTest
import uk.gov.justice.digital.hmpps.prisonersearch.search.model.PrisonerBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.search.repository.PrisonerRepository

class AttributeSearchIntegrationTest : AbstractSearchDataIntegrationTest() {

  private val prisonerBuilders = mutableListOf<PrisonerBuilder>()

  // Although we want the super class to create a new index before the tests run, we don't want to load any data into it, so we can manage prisoners on a test by test basis
  override fun loadPrisonerData() {}

  private fun PrisonerRepository.delete(prisonerNumber: String) = delete(prisonerNumber, SyncIndex.GREEN)

  private fun WebTestClient.attributeSearch(body: String) =
    post()
      .uri("/attribute-search")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_SEARCH")))
      .header("Content-Type", "application/json")
      .bodyValue(body)
      .exchange()
      .expectStatus().isOk

  private fun loadPrisoners(vararg prisoners: PrisonerBuilder) =
    prisonerBuilders.apply {
      addAll(prisoners)
      loadPrisonersFromBuilders(this)
    }

  @BeforeEach
  fun `clear search repository`() {
    prisonerBuilders.onEach { prisonerRepository.delete(it.prisonerNumber) }.clear()
    waitForPrisonerLoading(0)
  }

  @Test
  fun `single string matcher`() {
    loadPrisoners(
      PrisonerBuilder(prisonerNumber = "A1234AA", firstName = "John"),
      PrisonerBuilder(prisonerNumber = "B1234BB", firstName = "Jeff"),
    )

    webTestClient.attributeSearch(
      """
        {
          "queries": [
            {
              "matchers": [ 
                {
                  "type": "String",
                  "attribute": "firstName",
                  "condition": "IS",
                  "searchTerm": "John"
                }
              ]
            }
          ]
        }
      """.trimIndent(),
    )
      .expectBody()
      .jsonPath("$.content.length()").isEqualTo(1)
      .jsonPath("$.content[0].prisonerNumber").isEqualTo("A1234AA")
  }

  @Test
  fun `AND string matchers`() {
    loadPrisoners(
      PrisonerBuilder(prisonerNumber = "A1234AA", firstName = "John", lastName = "Smith"),
      PrisonerBuilder(prisonerNumber = "B1234BB", firstName = "John", lastName = "Jones"),
      PrisonerBuilder(prisonerNumber = "C1234CC", firstName = "Jeff", lastName = "Smith"),
    )

    webTestClient.attributeSearch(
      """
        {
          "queries": [
            {
              "matchers": [ 
                {
                  "type": "String",
                  "attribute": "firstName",
                  "condition": "IS",
                  "searchTerm": "John"
                },
                {
                  "type": "String",
                  "attribute": "lastName",
                  "condition": "IS",
                  "searchTerm": "Smith"
                }
              ]
            }
          ]
        }
      """.trimIndent(),
    )
      .expectBody()
      .jsonPath("$.content.length()").isEqualTo(1)
      .jsonPath("$.content[0].prisonerNumber").isEqualTo("A1234AA")
  }

  @Test
  fun `OR string matchers`() {
    loadPrisoners(
      PrisonerBuilder(prisonerNumber = "A1234AA", firstName = "John", lastName = "Smith"),
      PrisonerBuilder(prisonerNumber = "B1234BB", firstName = "Jeff", lastName = "Jones"),
      PrisonerBuilder(prisonerNumber = "C1234CC", firstName = "Jimmy", lastName = "Power"),
    )

    webTestClient.attributeSearch(
      """
        {
          "queries": [
            {
              "joinType": "OR",
              "matchers": [ 
                {
                  "type": "String",
                  "attribute": "firstName",
                  "condition": "IS",
                  "searchTerm": "John"
                },
                {
                  "type": "String",
                  "attribute": "lastName",
                  "condition": "IS",
                  "searchTerm": "Jones"
                }
              ]
            }
          ]
        }
      """.trimIndent(),
    )
      .expectBody()
      .jsonPath("$.content.length()").isEqualTo(2)
      .jsonPath("$.content[0].prisonerNumber").isEqualTo("A1234AA")
      .jsonPath("$.content[1].prisonerNumber").isEqualTo("B1234BB")
  }
}
