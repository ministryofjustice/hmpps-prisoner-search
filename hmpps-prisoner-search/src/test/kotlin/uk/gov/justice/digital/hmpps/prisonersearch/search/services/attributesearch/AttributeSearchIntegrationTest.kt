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
import uk.gov.justice.digital.hmpps.prisonersearch.search.model.PhysicalCharacteristicBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.search.model.PrisonerBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.search.model.ProfileInformationBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.search.repository.PrisonerRepository
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.AttributeSearchRequest
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.JoinType
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.JoinType.OR
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.requestdsl.AND_LT
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.requestdsl.AND_LTE
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.requestdsl.CONTAINS
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.requestdsl.EQ
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.requestdsl.GT
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.requestdsl.GTE
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.requestdsl.IS
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.requestdsl.IS_NOT
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.requestdsl.LT
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.requestdsl.LTE
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.requestdsl.RequestDsl
import java.time.LocalDate
import java.time.LocalDateTime

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

  @Nested
  inner class IntMatchers {
    @Test
    fun `single equals`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", heightCentimetres = 160),
        PrisonerBuilder(prisonerNumber = "B1234BB", heightCentimetres = 170),
      )

      val request = RequestDsl {
        query {
          intMatcher("heightCentimetres" EQ 160)
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("A1234AA")
    }

    @Test
    fun `single greater than or equal to`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", heightCentimetres = 160),
        PrisonerBuilder(prisonerNumber = "B1234BB", heightCentimetres = 170),
      )

      val request = RequestDsl {
        query {
          intMatcher("heightCentimetres" GTE 170)
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("B1234BB")
    }

    @Test
    fun `single greater than`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", heightCentimetres = 160),
        PrisonerBuilder(prisonerNumber = "B1234BB", heightCentimetres = 170),
      )

      val request = RequestDsl {
        query {
          intMatcher("heightCentimetres" GT 169)
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("B1234BB")
    }

    @Test
    fun `single less than or equal to`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", heightCentimetres = 160),
        PrisonerBuilder(prisonerNumber = "B1234BB", heightCentimetres = 170),
      )

      val request = RequestDsl {
        query {
          intMatcher("heightCentimetres" LTE 160)
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("A1234AA")
    }

    @Test
    fun `single less than`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", heightCentimetres = 160),
        PrisonerBuilder(prisonerNumber = "B1234BB", heightCentimetres = 170),
      )

      val request = RequestDsl {
        query {
          intMatcher("heightCentimetres" LT 161)
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("A1234AA")
    }

    @Test
    fun `between inclusive`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", heightCentimetres = 159),
        PrisonerBuilder(prisonerNumber = "B1234BB", heightCentimetres = 160),
        PrisonerBuilder(prisonerNumber = "C1234CC", heightCentimetres = 170),
        PrisonerBuilder(prisonerNumber = "D1234DD", heightCentimetres = 171),
      )

      val request = RequestDsl {
        query {
          intMatcher("heightCentimetres" GTE 160 AND_LTE 170)
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("B1234BB", "C1234CC")
    }

    @Test
    fun `between exclusive`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", heightCentimetres = 159),
        PrisonerBuilder(prisonerNumber = "B1234BB", heightCentimetres = 160),
        PrisonerBuilder(prisonerNumber = "C1234CC", heightCentimetres = 170),
        PrisonerBuilder(prisonerNumber = "D1234DD", heightCentimetres = 171),
      )

      val request = RequestDsl {
        query {
          intMatcher("heightCentimetres" GT 159 AND_LT 171)
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("B1234BB", "C1234CC")
    }

    @Test
    fun `AND with 2 integers`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", heightCentimetres = 161, physicalCharacteristics = shoeSize(8)),
        PrisonerBuilder(prisonerNumber = "B1234BB", heightCentimetres = 161, physicalCharacteristics = shoeSize(9)),
        PrisonerBuilder(prisonerNumber = "C1234CC", heightCentimetres = 160, physicalCharacteristics = shoeSize(8)),
      )

      val request = RequestDsl {
        query {
          intMatcher("heightCentimetres" GTE 161)
          intMatcher("shoeSize" LT 9)
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("A1234AA")
    }

    @Test
    fun `OR with 2 integers`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", heightCentimetres = 160, physicalCharacteristics = shoeSize(8)),
        PrisonerBuilder(prisonerNumber = "B1234BB", heightCentimetres = 161, physicalCharacteristics = shoeSize(9)),
        PrisonerBuilder(prisonerNumber = "C1234CC", heightCentimetres = 162, physicalCharacteristics = shoeSize(10)),
      )

      val request = RequestDsl {
        query {
          joinType = OR
          intMatcher("heightCentimetres" GT 161)
          intMatcher("shoeSize" LTE 8)
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("A1234AA", "C1234CC")
    }

    @Test
    fun `integers AND strings`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", heightCentimetres = 160, firstName = "John"),
        PrisonerBuilder(prisonerNumber = "B1234BB", heightCentimetres = 161, firstName = "John"),
        PrisonerBuilder(prisonerNumber = "C1234CC", heightCentimetres = 161, firstName = "Jack"),
      )

      val request = RequestDsl {
        query {
          intMatcher("heightCentimetres" EQ 161)
          stringMatcher("firstName" IS "John")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("B1234BB")
    }

    @Test
    fun `integers OR strings`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", heightCentimetres = 160, firstName = "John"),
        PrisonerBuilder(prisonerNumber = "B1234BB", heightCentimetres = 161, firstName = "Jack"),
        PrisonerBuilder(prisonerNumber = "C1234CC", heightCentimetres = 162, firstName = "Jack"),
      )

      val request = RequestDsl {
        query {
          joinType = OR
          intMatcher("heightCentimetres" EQ 161)
          stringMatcher("firstName" IS "John")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("A1234AA", "B1234BB")
    }
  }

  @Nested
  inner class BooleanMatchers {
    @Test
    fun `single true`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", recall = true),
        PrisonerBuilder(prisonerNumber = "B1234BB", recall = false),
      )

      val request = RequestDsl {
        query {
          booleanMatcher("recall" IS true)
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("A1234AA")
    }

    @Test
    fun `single false`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", recall = true),
        PrisonerBuilder(prisonerNumber = "B1234BB", recall = false),
      )

      val request = RequestDsl {
        query {
          booleanMatcher("recall" IS false)
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("B1234BB")
    }

    @Test
    fun `booleans with AND`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", recall = true, profileInformation = youthOffender(true)),
        PrisonerBuilder(prisonerNumber = "B1234BB", recall = true, profileInformation = youthOffender(false)),
        PrisonerBuilder(prisonerNumber = "C1234CC", recall = false, profileInformation = youthOffender(true)),
        PrisonerBuilder(prisonerNumber = "D1234DD", recall = false, profileInformation = youthOffender(false)),
      )

      val request = RequestDsl {
        query {
          booleanMatcher("recall" IS true)
          booleanMatcher("youthOffender" IS false)
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("B1234BB")
    }

    @Test
    fun `booleans with OR`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", recall = true, profileInformation = youthOffender(true)),
        PrisonerBuilder(prisonerNumber = "B1234BB", recall = true, profileInformation = youthOffender(false)),
        PrisonerBuilder(prisonerNumber = "C1234CC", recall = false, profileInformation = youthOffender(true)),
        PrisonerBuilder(prisonerNumber = "D1234DD", recall = false, profileInformation = youthOffender(false)),
      )

      val request = RequestDsl {
        query {
          joinType = OR
          booleanMatcher("recall" IS true)
          booleanMatcher("youthOffender" IS true)
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("A1234AA", "B1234BB", "C1234CC")
    }

    @Test
    fun `booleans AND strings AND integers`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", recall = true, firstName = "John", heightCentimetres = 170),
        PrisonerBuilder(prisonerNumber = "B1234BB", recall = true, firstName = "Jack", heightCentimetres = 170),
        PrisonerBuilder(prisonerNumber = "C1234CC", recall = true, firstName = "John", heightCentimetres = 171),
        PrisonerBuilder(prisonerNumber = "D1234DD", recall = false, firstName = "John", heightCentimetres = 170),
        PrisonerBuilder(prisonerNumber = "E1234EE", recall = false, firstName = "Jack", heightCentimetres = 170),
        PrisonerBuilder(prisonerNumber = "F1234FF", recall = false, firstName = "John", heightCentimetres = 171),
      )

      val request = RequestDsl {
        query {
          booleanMatcher("recall" IS false)
          stringMatcher("firstName" IS "John")
          intMatcher("heightCentimetres" EQ 171)
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("F1234FF")
    }

    @Test
    fun `booleans OR strings OR integers`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", recall = true, firstName = "John", heightCentimetres = 170),
        PrisonerBuilder(prisonerNumber = "B1234BB", recall = true, firstName = "Jack", heightCentimetres = 170),
        PrisonerBuilder(prisonerNumber = "C1234CC", recall = true, firstName = "John", heightCentimetres = 171),
        PrisonerBuilder(prisonerNumber = "D1234DD", recall = false, firstName = "John", heightCentimetres = 170),
        PrisonerBuilder(prisonerNumber = "E1234EE", recall = false, firstName = "Jack", heightCentimetres = 170),
        PrisonerBuilder(prisonerNumber = "F1234FF", recall = false, firstName = "John", heightCentimetres = 171),
      )

      val request = RequestDsl {
        query {
          joinType = OR
          booleanMatcher("recall" IS false)
          stringMatcher("firstName" IS "Jack")
          intMatcher("heightCentimetres" EQ 171)
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("B1234BB", "C1234CC", "D1234DD", "E1234EE", "F1234FF")
    }
  }

  @Nested
  inner class DateTimeMatchers {
    private val now = LocalDateTime.now()
    private val yesterday = now.minusDays(1)
    private val tomorrow = now.plusDays(1)

    @Test
    fun `single greater than`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", currentIncentive = IncentiveLevelBuilder(dateTime = yesterday)),
        PrisonerBuilder(prisonerNumber = "B1234BB", currentIncentive = IncentiveLevelBuilder(dateTime = now)),
      )

      val request = RequestDsl {
        query {
          dateTimeMatcher("currentIncentive.dateTime" GT now.minusHours(1))
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("B1234BB")
    }

    @Test
    fun `single less than`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", currentIncentive = IncentiveLevelBuilder(dateTime = yesterday)),
        PrisonerBuilder(prisonerNumber = "B1234BB", currentIncentive = IncentiveLevelBuilder(dateTime = now)),
      )

      val request = RequestDsl {
        query {
          dateTimeMatcher("currentIncentive.dateTime" LT now.minusHours(1))
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("A1234AA")
    }

    @Test
    fun `single between`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", currentIncentive = IncentiveLevelBuilder(dateTime = yesterday)),
        PrisonerBuilder(prisonerNumber = "B1234BB", currentIncentive = IncentiveLevelBuilder(dateTime = now)),
        PrisonerBuilder(prisonerNumber = "C1234CC", currentIncentive = IncentiveLevelBuilder(dateTime = tomorrow)),
      )

      val request = RequestDsl {
        query {
          dateTimeMatcher("currentIncentive.dateTime" GT now.minusHours(1) AND_LT now.plusHours(1))
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("B1234BB")
    }

    @Test
    fun `AND with greater than and less than`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", currentIncentive = IncentiveLevelBuilder(dateTime = yesterday)),
        PrisonerBuilder(prisonerNumber = "B1234BB", currentIncentive = IncentiveLevelBuilder(dateTime = now)),
        PrisonerBuilder(prisonerNumber = "C1234CC", currentIncentive = IncentiveLevelBuilder(dateTime = tomorrow)),
      )

      val request = RequestDsl {
        query {
          dateTimeMatcher("currentIncentive.dateTime" GT now.minusHours(1))
          dateTimeMatcher("currentIncentive.dateTime" LT now.plusHours(1))
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("B1234BB")
    }

    @Test
    fun `OR with less than and greater than`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", currentIncentive = IncentiveLevelBuilder(dateTime = yesterday)),
        PrisonerBuilder(prisonerNumber = "B1234BB", currentIncentive = IncentiveLevelBuilder(dateTime = now)),
        PrisonerBuilder(prisonerNumber = "C1234CC", currentIncentive = IncentiveLevelBuilder(dateTime = tomorrow)),
      )

      val request = RequestDsl {
        query {
          joinType = OR
          dateTimeMatcher("currentIncentive.dateTime" LT now.minusHours(1))
          dateTimeMatcher("currentIncentive.dateTime" GT now.plusHours(1))
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("A1234AA", "C1234CC")
    }

    @Test
    fun `AND with a string matcher`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", firstName = "John", currentIncentive = IncentiveLevelBuilder(dateTime = yesterday)),
        PrisonerBuilder(prisonerNumber = "B1234BB", firstName = "John", currentIncentive = IncentiveLevelBuilder(dateTime = now)),
        PrisonerBuilder(prisonerNumber = "C1234CC", firstName = "Jack", currentIncentive = IncentiveLevelBuilder(dateTime = yesterday)),
        PrisonerBuilder(prisonerNumber = "D1234DD", firstName = "Jack", currentIncentive = IncentiveLevelBuilder(dateTime = now)),
      )

      val request = RequestDsl {
        query {
          dateTimeMatcher("currentIncentive.dateTime" GT now.minusHours(1))
          stringMatcher("firstName" IS "John")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("B1234BB")
    }

    @Test
    fun `OR with a string matcher`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", firstName = "John", currentIncentive = IncentiveLevelBuilder(dateTime = yesterday)),
        PrisonerBuilder(prisonerNumber = "B1234BB", firstName = "John", currentIncentive = IncentiveLevelBuilder(dateTime = now)),
        PrisonerBuilder(prisonerNumber = "C1234CC", firstName = "Jack", currentIncentive = IncentiveLevelBuilder(dateTime = yesterday)),
        PrisonerBuilder(prisonerNumber = "D1234DD", firstName = "Jack", currentIncentive = IncentiveLevelBuilder(dateTime = now)),
      )

      val request = RequestDsl {
        query {
          joinType = OR
          dateTimeMatcher("currentIncentive.dateTime" GT now.minusHours(1))
          stringMatcher("firstName" IS "John")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("A1234AA", "B1234BB", "D1234DD")
    }

    @Test
    fun `AND with an int matcher`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", heightCentimetres = 170, currentIncentive = IncentiveLevelBuilder(dateTime = yesterday)),
        PrisonerBuilder(prisonerNumber = "B1234BB", heightCentimetres = 170, currentIncentive = IncentiveLevelBuilder(dateTime = now)),
        PrisonerBuilder(prisonerNumber = "C1234CC", heightCentimetres = 169, currentIncentive = IncentiveLevelBuilder(dateTime = yesterday)),
        PrisonerBuilder(prisonerNumber = "D1234DD", heightCentimetres = 169, currentIncentive = IncentiveLevelBuilder(dateTime = now)),
      )

      val request = RequestDsl {
        query {
          dateTimeMatcher("currentIncentive.dateTime" GT now.minusHours(1))
          intMatcher("heightCentimetres" EQ 170)
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("B1234BB")
    }

    @Test
    fun `OR with an int matcher`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", heightCentimetres = 170, currentIncentive = IncentiveLevelBuilder(dateTime = yesterday)),
        PrisonerBuilder(prisonerNumber = "B1234BB", heightCentimetres = 170, currentIncentive = IncentiveLevelBuilder(dateTime = now)),
        PrisonerBuilder(prisonerNumber = "C1234CC", heightCentimetres = 169, currentIncentive = IncentiveLevelBuilder(dateTime = yesterday)),
        PrisonerBuilder(prisonerNumber = "D1234DD", heightCentimetres = 169, currentIncentive = IncentiveLevelBuilder(dateTime = now)),
      )

      val request = RequestDsl {
        query {
          joinType = OR
          dateTimeMatcher("currentIncentive.dateTime" GT now.minusHours(1))
          intMatcher("heightCentimetres" EQ 170)
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("A1234AA", "B1234BB", "D1234DD")
    }
  }

  @Nested
  inner class DateMatchers {
    @Test
    fun `single equals`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", dateOfBirth = "1989-12-31"),
        PrisonerBuilder(prisonerNumber = "B1234BB", dateOfBirth = "1990-01-01"),
      )

      val request = RequestDsl {
        query {
          dateMatcher("dateOfBirth" EQ "1989-12-31")
        }
      }

      webTestClient.attributeSearch(request)
        .expectPrisoners("A1234AA")
    }

    @Test
    fun `single greater than or equal to`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", dateOfBirth = "1989-12-31"),
        PrisonerBuilder(prisonerNumber = "B1234BB", dateOfBirth = "1990-01-01"),
      )

      val request = RequestDsl {
        query {
          dateMatcher("dateOfBirth" GTE "1990-01-01")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("B1234BB")
    }

    @Test
    fun `single greater than`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", dateOfBirth = "1989-12-31"),
        PrisonerBuilder(prisonerNumber = "B1234BB", dateOfBirth = "1990-01-01"),
      )

      val request = RequestDsl {
        query {
          dateMatcher("dateOfBirth" GT "1989-12-31")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("B1234BB")
    }

    @Test
    fun `single less than or equal to`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", dateOfBirth = "1989-12-31"),
        PrisonerBuilder(prisonerNumber = "B1234BB", dateOfBirth = "1990-01-01"),
      )

      val request = RequestDsl {
        query {
          dateMatcher("dateOfBirth" LTE "1989-12-31")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("A1234AA")
    }

    @Test
    fun `single less than`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", dateOfBirth = "1989-12-31"),
        PrisonerBuilder(prisonerNumber = "B1234BB", dateOfBirth = "1990-01-01"),
      )

      val request = RequestDsl {
        query {
          dateMatcher("dateOfBirth" LT "1990-01-01")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("A1234AA")
    }

    @Test
    fun `between inclusive`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", dateOfBirth = "1989-12-30"),
        PrisonerBuilder(prisonerNumber = "B1234BB", dateOfBirth = "1989-12-31"),
        PrisonerBuilder(prisonerNumber = "C1234CC", dateOfBirth = "1990-01-01"),
        PrisonerBuilder(prisonerNumber = "D1234DD", dateOfBirth = "1990-01-02"),
      )

      val request = RequestDsl {
        query {
          dateMatcher("dateOfBirth" GTE "1989-12-31" AND_LTE "1990-01-01")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("B1234BB", "C1234CC")
    }

    @Test
    fun `between exclusive`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", dateOfBirth = "1989-12-30"),
        PrisonerBuilder(prisonerNumber = "B1234BB", dateOfBirth = "1989-12-31"),
        PrisonerBuilder(prisonerNumber = "C1234CC", dateOfBirth = "1990-01-01"),
        PrisonerBuilder(prisonerNumber = "D1234DD", dateOfBirth = "1990-01-02"),
      )

      val request = RequestDsl {
        query {
          dateMatcher("dateOfBirth" GT "1989-12-30" AND_LT "1990-01-02")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("B1234BB", "C1234CC")
    }

    @Test
    fun `AND with 2 dates`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", dateOfBirth = "1989-12-30", receptionDate = "2020-12-30"),
        PrisonerBuilder(prisonerNumber = "B1234BB", dateOfBirth = "1989-12-31", receptionDate = "2020-12-31"),
        PrisonerBuilder(prisonerNumber = "C1234CC", dateOfBirth = "1990-01-01", receptionDate = "2021-01-01"),
      )

      val request = RequestDsl {
        query {
          dateMatcher("dateOfBirth" GTE "1989-12-31")
          dateMatcher("receptionDate" LT "2021-01-01")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("B1234BB")
    }

    @Test
    fun `OR with 2 dates`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", dateOfBirth = "1989-12-30", receptionDate = "2020-12-30"),
        PrisonerBuilder(prisonerNumber = "B1234BB", dateOfBirth = "1989-12-31", receptionDate = "2020-12-31"),
        PrisonerBuilder(prisonerNumber = "C1234CC", dateOfBirth = "1990-01-01", receptionDate = "2021-01-01"),
      )

      val request = RequestDsl {
        query {
          joinType = OR
          dateMatcher("dateOfBirth" GT "1989-12-31")
          dateMatcher("receptionDate" LTE "2020-12-30")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("A1234AA", "C1234CC")
    }

    @Test
    fun `dates AND strings`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", dateOfBirth = "1989-12-30", firstName = "John"),
        PrisonerBuilder(prisonerNumber = "B1234BB", dateOfBirth = "1989-12-31", firstName = "John"),
        PrisonerBuilder(prisonerNumber = "C1234CC", dateOfBirth = "1989-12-31", firstName = "Jack"),
      )

      val request = RequestDsl {
        query {
          dateMatcher("dateOfBirth" EQ "1989-12-31")
          stringMatcher("firstName" IS "John")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("B1234BB")
    }

    @Test
    fun `dates OR strings`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", dateOfBirth = "1989-12-30", firstName = "John"),
        PrisonerBuilder(prisonerNumber = "B1234BB", dateOfBirth = "1989-12-31", firstName = "Jack"),
        PrisonerBuilder(prisonerNumber = "C1234CC", dateOfBirth = "1990-01-01", firstName = "Jack"),
      )

      val request = RequestDsl {
        query {
          joinType = OR
          dateMatcher("dateOfBirth" EQ "1989-12-31")
          stringMatcher("firstName" IS "John")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("A1234AA", "B1234BB")
    }
  }

  @Nested
  inner class SubQueries {
    private val today = LocalDate.now()
    private val yesterday = today.minusDays(1)
    private val tomorrow = today.plusDays(1)

    @Test
    fun `single sub-query`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", firstName = "John"),
        PrisonerBuilder(prisonerNumber = "B1234BB", firstName = "Jeff"),
        PrisonerBuilder(prisonerNumber = "C1234CC", firstName = "Johnson"),
      )

      val request = RequestDsl {
        query {
          subQuery {
            stringMatcher("firstName" IS "John")
          }
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("A1234AA")
    }

    @Test
    fun `single sub-query with multiple matchers`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", firstName = "John", receptionDate = "$yesterday"),
        PrisonerBuilder(prisonerNumber = "B1234BB", firstName = "John", receptionDate = "$today"),
        PrisonerBuilder(prisonerNumber = "C1234CC", firstName = "Johnson", receptionDate = "$tomorrow"),
      )

      val request = RequestDsl {
        query {
          subQuery {
            stringMatcher("firstName" IS "John")
            dateMatcher("receptionDate" EQ "$yesterday")
          }
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("A1234AA")
    }

    @Test
    fun `single sub-query with multiple OR matchers`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", firstName = "John", receptionDate = "$yesterday"),
        PrisonerBuilder(prisonerNumber = "B1234BB", firstName = "Jack", receptionDate = "$today"),
        PrisonerBuilder(prisonerNumber = "C1234CC", firstName = "Johnson", receptionDate = "$tomorrow"),
      )

      val request = RequestDsl {
        query {
          subQuery {
            joinType = OR
            stringMatcher("firstName" IS "John")
            dateMatcher("receptionDate" EQ "$today")
          }
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("A1234AA", "B1234BB")
    }

    @Test
    fun `multiple sub-queries with AND`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", firstName = "John", lastName = "Smith"),
        PrisonerBuilder(prisonerNumber = "B1234BB", firstName = "Jeff", lastName = "Smith"),
        PrisonerBuilder(prisonerNumber = "C1234CC", firstName = "John", lastName = "Jones"),
      )

      val request = RequestDsl {
        query {
          subQuery {
            stringMatcher("firstName" IS "John")
          }
          subQuery {
            stringMatcher("lastName" IS "Smith")
          }
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("A1234AA")
    }

    @Test
    fun `multiple sub-queries with OR`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", firstName = "John", lastName = "Smith"),
        PrisonerBuilder(prisonerNumber = "B1234BB", firstName = "Jeff", lastName = "Smith"),
        PrisonerBuilder(prisonerNumber = "C1234CC", firstName = "Johnson", lastName = "Jones"),
      )

      val request = RequestDsl {
        query {
          joinType = OR
          subQuery {
            stringMatcher("firstName" IS "John")
          }
          subQuery {
            stringMatcher("firstName" IS "Jeff")
          }
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("A1234AA", "B1234BB")
    }

    @Test
    fun `matcher with single sub-query`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", firstName = "John", receptionDate = "$yesterday"),
        PrisonerBuilder(prisonerNumber = "B1234BB", firstName = "John", receptionDate = "$today"),
        PrisonerBuilder(prisonerNumber = "C1234CC", firstName = "Johnson", receptionDate = "$tomorrow"),
      )

      val request = RequestDsl {
        query {
          stringMatcher("firstName" IS "John")
          subQuery {
            dateMatcher("receptionDate" EQ "$yesterday")
          }
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("A1234AA")
    }

    @Test
    fun `matcher with single sub-query using OR`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", firstName = "John", receptionDate = "$yesterday"),
        PrisonerBuilder(prisonerNumber = "B1234BB", firstName = "Jack", receptionDate = "$today"),
        PrisonerBuilder(prisonerNumber = "C1234CC", firstName = "Johnson", receptionDate = "$tomorrow"),
      )

      val request = RequestDsl {
        query {
          joinType = OR
          stringMatcher("firstName" IS "John")
          subQuery {
            dateMatcher("receptionDate" EQ "$today")
          }
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("A1234AA", "B1234BB")
    }

    @Test
    fun `multiple nested sub-queries`() {
      loadPrisoners(
        PrisonerBuilder(prisonerNumber = "A1234AA", firstName = "John", receptionDate = "$yesterday"),
        PrisonerBuilder(prisonerNumber = "B1234BB", firstName = "John", receptionDate = "$today"),
        PrisonerBuilder(prisonerNumber = "C1234CC", firstName = "John", receptionDate = "$tomorrow"),
        PrisonerBuilder(prisonerNumber = "D1234DD", firstName = "Johnson", receptionDate = "$today"),
      )

      // firstName = John AND ((receptionDate = yesterday) OR (receptionDate = today))
      val request = RequestDsl {
        query {
          joinType = JoinType.AND
          stringMatcher("firstName" IS "John")
          subQuery {
            joinType = OR
            subQuery {
              dateMatcher("receptionDate" EQ "$yesterday")
            }
            subQuery {
              dateMatcher("receptionDate" EQ "$today")
            }
          }
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("A1234AA", "B1234BB")
    }
  }

  private fun youthOffender(condition: Boolean) = ProfileInformationBuilder(youthOffender = condition)

  private fun shoeSize(size: Int) = PhysicalCharacteristicBuilder(shoeSize = size)

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
