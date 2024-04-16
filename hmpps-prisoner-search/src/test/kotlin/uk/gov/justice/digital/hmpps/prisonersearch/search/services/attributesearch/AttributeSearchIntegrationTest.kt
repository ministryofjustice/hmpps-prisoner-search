package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.prisonersearch.search.AbstractSearchIntegrationTest
import uk.gov.justice.digital.hmpps.prisonersearch.search.model.BodyPartBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.search.model.IncentiveLevelBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.search.model.PhysicalMarkBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.search.model.PrisonerBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.search.model.ProfileInformationBuilder
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
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.requestdsl.STARTSWITH
import java.time.LocalDateTime

/**
 * Test the attribute search engine.
 *
 * Note that this class builds AttributeSearchRequest instances using the [RequestDsl]. If you want to know how the requests
 * look in JSON format see test class [AttributeSearchRequestJsonTest].
 */
class AttributeSearchIntegrationTest : AbstractSearchIntegrationTest() {
  private val now = LocalDateTime.now()

  // Create 3 prisoners with predictable data we can search for
  val prisoners = listOf(
    PrisonerBuilder(
      prisonerNumber = "P1",
      firstName = "John1",
      dateOfBirth = "1990-01-01",
      heightCentimetres = 181,
      recall = false,
      currentIncentive = IncentiveLevelBuilder(levelDescription = "Incentive level 1", dateTime = now.minusDays(2).minusHours(1)),
      alertCodes = listOf("AT1" to "AC1", "AT2" to "AC2"),
      profileInformation = ProfileInformationBuilder(youthOffender = true),
      physicalMarks = PhysicalMarkBuilder(
        tattoo = listOf(BodyPartBuilder(bodyPart = "Arm", comment = "dragon")),
        scar = listOf(BodyPartBuilder(bodyPart = "Arm", comment = "bite")),
        mark = listOf(BodyPartBuilder(bodyPart = "Arm", comment = "birthmark")),
      ),
      cellLocation = "1-1-002",
    ),
    PrisonerBuilder(
      prisonerNumber = "P2",
      firstName = "James2",
      dateOfBirth = "1990-01-02",
      heightCentimetres = 182,
      recall = true,
      currentIncentive = IncentiveLevelBuilder(levelDescription = "Incentive level 2", dateTime = now.minusDays(1).minusHours(1)),
      alertCodes = listOf("AT1" to "AC2", "AT2" to "AC2"),
      profileInformation = ProfileInformationBuilder(youthOffender = false),
      physicalMarks = PhysicalMarkBuilder(
        tattoo = listOf(BodyPartBuilder(bodyPart = "Arm", comment = "dragon/skull")),
        scar = listOf(BodyPartBuilder(bodyPart = "Arm", comment = "dog bite")),
        mark = listOf(BodyPartBuilder(bodyPart = "Arm", comment = "birthmark on left wrist")),
      ),
      cellLocation = "TAP",
    ),
    PrisonerBuilder(
      prisonerNumber = "P3",
      firstName = "Jeff3",
      dateOfBirth = "1990-01-03",
      heightCentimetres = 183,
      recall = false,
      currentIncentive = IncentiveLevelBuilder(levelDescription = "Incentive level 3", dateTime = now.minusHours(1)),
      alertCodes = listOf("AT3" to "AC3"),
      profileInformation = ProfileInformationBuilder(youthOffender = true),
      physicalMarks = PhysicalMarkBuilder(
        tattoo = listOf(
          BodyPartBuilder(bodyPart = "Arm", comment = "love heart"),
          BodyPartBuilder(bodyPart = "Shoulder", comment = "dragon"),
        ),
        scar = listOf(
          BodyPartBuilder(bodyPart = "Arm", comment = "wrist"),
          BodyPartBuilder(bodyPart = "Shoulder", comment = "bite"),
        ),
        mark = listOf(
          BodyPartBuilder(bodyPart = "Arm"),
          BodyPartBuilder(bodyPart = "Shoulder", comment = "birthmark"),
        ),
      ),
      cellLocation = "1-2-012",
    ),
  )

  override fun loadPrisonerData() {
    loadPrisonersFromBuilders(prisoners)
  }

  @Nested
  inner class StringMatchers {
    @Test
    fun `single IS`() {
      val request = RequestDsl {
        query {
          stringMatcher("firstName" IS "John1")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1")
    }

    @Test
    fun `single IS on an attribute without a keyword defined`() {
      val request = RequestDsl {
        query {
          stringMatcher("currentIncentive.level.description" IS "Incentive level 1")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1")
    }

    @Test
    fun `single IS uppercase`() {
      val request = RequestDsl {
        query {
          stringMatcher("firstName" IS "JOHN1")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1")
    }

    @Test
    fun `single IS lowercase`() {
      val request = RequestDsl {
        query {
          stringMatcher("firstName" IS "john1")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1")
    }

    @Test
    fun `single IS with single character wildcard does not work`() {
      val request = RequestDsl {
        query {
          stringMatcher("firstName" IS "john?")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners()
    }

    @Test
    fun `single IS with multiple character wildcard does not work`() {
      val request = RequestDsl {
        query {
          stringMatcher("firstName" IS "john*")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners()
    }

    @Test
    fun `IS with AND`() {
      val request = RequestDsl {
        query {
          stringMatcher("prisonerNumber" IS "P1")
          stringMatcher("firstName" IS "John1")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1")
    }

    @Test
    fun `IS with OR`() {
      val request = RequestDsl {
        query {
          joinType = OR
          stringMatcher("firstName" IS "John1")
          stringMatcher("prisonerNumber" IS "P2")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1", "P2")
    }

    @Test
    fun `single IS_NOT`() {
      val request = RequestDsl {
        query {
          stringMatcher("firstName" IS_NOT "John1")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P2", "P3")
    }

    @Test
    fun `single IS_NOT uppercase`() {
      val request = RequestDsl {
        query {
          stringMatcher("firstName" IS_NOT "JOHN1")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P2", "P3")
    }

    @Test
    fun `single IS_NOT lowercase`() {
      val request = RequestDsl {
        query {
          stringMatcher("firstName" IS_NOT "john1")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P2", "P3")
    }

    @Test
    fun `single IS_NOT on an attribute without a keyword defined`() {
      val request = RequestDsl {
        query {
          stringMatcher("currentIncentive.level.description" IS_NOT "Incentive level 1")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P2", "P3")
    }

    @Test
    fun `IS_NOT with AND`() {
      val request = RequestDsl {
        query {
          stringMatcher("firstName" IS_NOT "John1")
          stringMatcher("firstName" IS_NOT "James2")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P3")
    }

    @Test
    fun `IS_NOT with OR`() {
      val request = RequestDsl {
        query {
          joinType = OR
          stringMatcher("firstName" IS_NOT "John1")
          stringMatcher("currentIncentive.level.description" IS_NOT "Incentive level 2")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1", "P2", "P3")
    }

    @Test
    fun `single CONTAINS`() {
      val request = RequestDsl {
        query {
          stringMatcher("firstName" CONTAINS "ohn")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1")
    }

    @Test
    fun `single CONTAINS uppercase`() {
      val request = RequestDsl {
        query {
          stringMatcher("firstName" CONTAINS "OHN")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1")
    }

    @Test
    fun `single CONTAINS lowercase`() {
      val request = RequestDsl {
        query {
          stringMatcher("firstName" CONTAINS "ohn")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1")
    }

    @Test
    fun `single CONTAINS with single character wildcard`() {
      val request = RequestDsl {
        query {
          stringMatcher("currentIncentive.level.description" CONTAINS "ince?tive level 1")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1")
    }

    @Test
    fun `single CONTAINS with single character wildcard no match`() {
      val request = RequestDsl {
        query {
          stringMatcher("currentIncentive.level.description" CONTAINS "ince?ve level")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners()
    }

    @Test
    fun `single CONTAINS with more than one single character wildcard`() {
      val request = RequestDsl {
        query {
          stringMatcher("currentIncentive.level.description" CONTAINS "ince?tive le?el 1")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1")
    }

    @Test
    fun `single CONTAINS with more than one single character wildcard no match`() {
      val request = RequestDsl {
        query {
          stringMatcher("currentIncentive.level.description" CONTAINS "ince?ve le?el")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners()
    }

    @Test
    fun `single CONTAINS with a multiple character wildcard`() {
      val request = RequestDsl {
        query {
          stringMatcher("currentIncentive.level.description" CONTAINS "ince*ve level 1")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1")
    }

    @Test
    fun `single CONTAINS with a multiple character wildcard no match`() {
      val request = RequestDsl {
        query {
          stringMatcher("currentIncentive.level.description" CONTAINS "ince*ves level")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners()
    }

    @Test
    fun `single CONTAINS with more than one multiple character wildcards`() {
      val request = RequestDsl {
        query {
          stringMatcher("currentIncentive.level.description" CONTAINS "ince*ve l*l 1")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1")
    }

    @Test
    fun `single CONTAINS with more than one multiple character wildcards no match`() {
      val request = RequestDsl {
        query {
          stringMatcher("currentIncentive.level.description" CONTAINS "ince*ves l*l")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners()
    }

    @Test
    fun `single CONTAINS with wildcard not a full match`() {
      val request = RequestDsl {
        query {
          stringMatcher("currentIncentive.level.description" CONTAINS "e*ve l*l")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners()
    }

    @Test
    fun `CONTAINS with AND`() {
      val request = RequestDsl {
        query {
          stringMatcher("firstName" CONTAINS "John")
          stringMatcher("currentIncentive.level.description" CONTAINS "level 1")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1")
    }

    @Test
    fun `CONTAINS with OR`() {
      val request = RequestDsl {
        query {
          joinType = OR
          stringMatcher("firstName" CONTAINS "John1")
          stringMatcher("currentIncentive.level.description" CONTAINS "level 2")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1", "P2")
    }

    @Test
    fun `IS, CONTAINS and IS_NOT`() {
      val request = RequestDsl {
        query {
          stringMatcher("prisonerNumber" IS "P1")
          stringMatcher("prisonerNumber" CONTAINS "P")
          stringMatcher("prisonerNumber" IS_NOT "P2")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1")
    }

    @Test
    fun `IS, CONTAINS and IS_NOT with OR`() {
      val request = RequestDsl {
        query {
          joinType = OR
          stringMatcher("prisonerNumber" IS "P1")
          stringMatcher("prisonerNumber" CONTAINS "P")
          stringMatcher("prisonerNumber" IS_NOT "P2")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1", "P2", "P3")
    }

    @Test
    fun `IS with field from an array`() {
      val request = RequestDsl {
        query {
          stringMatcher("alerts.alertType" IS "AT1")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1", "P2")
    }

    @Test
    fun `IS with AND fields from an array`() {
      val request = RequestDsl {
        query {
          stringMatcher("alerts.alertType" IS "AT1")
          stringMatcher("alerts.alertCode" IS "AC2")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P2")
    }

    @Test
    fun `IS with OR fields from an array`() {
      val request = RequestDsl {
        query {
          joinType = OR
          stringMatcher("alerts.alertType" IS "AT1")
          stringMatcher("alerts.alertType" IS "AT2")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1", "P2")
    }

    @Test
    fun `IS with field from a nested object`() {
      val request = RequestDsl {
        query {
          stringMatcher("currentIncentive.level.description" IS "Incentive level 1")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1")
    }

    @Test
    fun `IS with AND fields from a nested object`() {
      // This doesn't make sense - incentive cannot be both Incentive level 1 and 2 - but just want to make sure it returns nothing
      val request = RequestDsl {
        query {
          stringMatcher("currentIncentive.level.description" IS "Incentive level 1")
          stringMatcher("currentIncentive.level.description" IS "Incentive level 2")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners()
    }

    @Test
    fun `IS_NOT with field from an array`() {
      val request = RequestDsl {
        query {
          stringMatcher("alerts.alertCode" IS_NOT "AC1")
          stringMatcher("alerts.alertCode" IS_NOT "AC2")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P3")
    }

    @Test
    fun `IS_NOT with AND fields from an array`() {
      val request = RequestDsl {
        query {
          stringMatcher("alerts.alertType" IS_NOT "AT1")
          stringMatcher("alerts.alertType" IS_NOT "AT2")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P3")
    }

    @Test
    fun `IS_NOT with OR fields from an array`() {
      val request = RequestDsl {
        query {
          joinType = OR
          stringMatcher("alerts.alertType" IS_NOT "AT1")
          stringMatcher("alerts.alertType" IS_NOT "AT2")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P3")
    }

    @Test
    fun `IS_NOT with field from a nested object`() {
      val request = RequestDsl {
        query {
          stringMatcher("currentIncentive.level.description" IS_NOT "Incentive level 1")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P2", "P3")
    }

    @Test
    fun `CONTAINS with field from an array`() {
      val request = RequestDsl {
        query {
          stringMatcher("alerts.alertType" CONTAINS "AT")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1", "P2", "P3")
    }

    @Test
    fun `CONTAINS with AND fields from an array`() {
      val request = RequestDsl {
        query {
          stringMatcher("alerts.alertType" CONTAINS "AT1")
          stringMatcher("alerts.alertCode" CONTAINS "AC1")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1")
    }

    @Test
    fun `CONTAINS with OR fields from an array`() {
      val request = RequestDsl {
        query {
          joinType = OR
          stringMatcher("alerts.alertType" CONTAINS "AT1")
          stringMatcher("alerts.alertCode" CONTAINS "AC2")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1", "P2")
    }

    @Test
    fun `CONTAINS with field from a nested object`() {
      val request = RequestDsl {
        query {
          stringMatcher("currentIncentive.level.description" CONTAINS "Incentive level 1")
          stringMatcher("currentIncentive.level.description" CONTAINS "Incentive level 1")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1")
    }

    @Test
    fun `fuzzy attribute with IS allows spelling mistakes`() {
      val request = RequestDsl {
        query {
          stringMatcher("firstName" IS "Jhon1")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1")
    }

    @Test
    fun `fuzzy attribute wth CONTAINS allows spelling mistakes`() {
      val request = RequestDsl {
        query {
          stringMatcher("tattoos.comment" CONTAINS "haert")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P3")
    }

    @Test
    fun `fuzzy attribute with IS doesn't allow incorrect case spelling mistakes`() {
      val request = RequestDsl {
        query {
          stringMatcher("firstName" IS "JHON1")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners()
    }

    @Test
    fun `fuzzy attribute with CONTAINS allows incorrect case spelling mistakes`() {
      val request = RequestDsl {
        query {
          stringMatcher("tattoos.comment" CONTAINS "HAERT")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P3")
    }

    @Test
    fun `non-fuzzy attribute with IS doesn't allow spelling mistakes`() {
      val request = RequestDsl {
        query {
          stringMatcher("currentIncentive.level.description" IS "Icnentive level 1")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners()
    }

    @Test
    fun `non-fuzzy attribute with CONTAINS doesn't allow spelling mistakes`() {
      val request = RequestDsl {
        query {
          stringMatcher("currentIncentive.level.description" CONTAINS "Icnentive")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners()
    }

    @Test
    fun `allows spelling mistakes in multiple fuzzy attributes`() {
      val request = RequestDsl {
        query {
          stringMatcher("firstName" IS "Jmaes2")
          stringMatcher("marks.comment" CONTAINS "brithmrak")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P2")
    }

    @Test
    fun `shouldn't perform a fuzzy search if wildcards used`() {
      val request = RequestDsl {
        query {
          stringMatcher("marks.bodyPart" IS "Arm")
          stringMatcher("marks.comment" CONTAINS "birthmark*wrist")
        }
      }

      // Shouldn't find P1 who has Arm/birthmark because the * should preclude a fuzzy search
      webTestClient.attributeSearch(request).expectPrisoners("P2")
    }

    @Test
    fun `single STARTSWITH`() {
      val request = RequestDsl {
        query {
          stringMatcher("cellLocation" STARTSWITH "1-")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1", "P3")
    }

    @Test
    fun `single STARTSWITH lowercase`() {
      val request = RequestDsl {
        query {
          stringMatcher("firstName" STARTSWITH "john")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1")
    }

    @Test
    fun `single STARTSWITH uppercase`() {
      val request = RequestDsl {
        query {
          stringMatcher("firstName" STARTSWITH "JOHN")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1")
    }

    @Test
    fun `STARTSWITH with AND`() {
      val request = RequestDsl {
        query {
          stringMatcher("cellLocation" STARTSWITH "1-")
          stringMatcher("firstName" IS "John1")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1")
    }

    @Test
    fun `STARTSWITH with OR`() {
      val request = RequestDsl {
        query {
          joinType = OR
          stringMatcher("cellLocation" STARTSWITH "1-")
          stringMatcher("firstName" IS "James2")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1", "P2", "P3")
    }

    @Test
    fun `STARTSWITH with IS_NOT`() {
      val request = RequestDsl {
        query {
          stringMatcher("cellLocation" STARTSWITH "1-")
          stringMatcher("cellLocation" IS_NOT "1-2-012")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1")
    }

    @Test
    fun `STARTSWITH with CONTAINS`() {
      val request = RequestDsl {
        query {
          stringMatcher("cellLocation" STARTSWITH "1-")
          stringMatcher("firstName" CONTAINS "John")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1")
    }

    @Test
    fun `STARTSWITH for an array`() {
      val request = RequestDsl {
        query {
          stringMatcher("marks.comment" STARTSWITH "birthmark")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1", "P2", "P3")
    }

    @Test
    fun `STARTSWITH for a nested object`() {
      val request = RequestDsl {
        query {
          stringMatcher("currentIncentive.level.description" STARTSWITH "Incentive")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1", "P2", "P3")
    }
  }

  @Nested
  inner class IntMatchers {
    @Test
    fun `single equals`() {
      val request = RequestDsl {
        query {
          intMatcher("heightCentimetres" EQ 181)
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1")
    }

    @Test
    fun `single greater than or equal to`() {
      val request = RequestDsl {
        query {
          intMatcher("heightCentimetres" GTE 183)
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P3")
    }

    @Test
    fun `single greater than`() {
      val request = RequestDsl {
        query {
          intMatcher("heightCentimetres" GT 182)
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P3")
    }

    @Test
    fun `single less than or equal to`() {
      val request = RequestDsl {
        query {
          intMatcher("heightCentimetres" LTE 181)
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1")
    }

    @Test
    fun `single less than`() {
      val request = RequestDsl {
        query {
          intMatcher("heightCentimetres" LT 182)
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1")
    }

    @Test
    fun `between inclusive`() {
      val request = RequestDsl {
        query {
          intMatcher("heightCentimetres" GTE 181 AND_LTE 182)
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1", "P2")
    }

    @Test
    fun `between exclusive`() {
      val request = RequestDsl {
        query {
          intMatcher("heightCentimetres" GT 181 AND_LT 183)
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P2")
    }

    @Test
    fun `AND with 2 integers`() {
      val request = RequestDsl {
        query {
          intMatcher("heightCentimetres" GTE 181)
          intMatcher("heightCentimetres" LT 182)
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1")
    }

    @Test
    fun `OR with 2 integers`() {
      val request = RequestDsl {
        query {
          joinType = OR
          intMatcher("heightCentimetres" GT 182)
          intMatcher("heightCentimetres" LTE 181)
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1", "P3")
    }

    @Test
    fun `integers AND strings`() {
      val request = RequestDsl {
        query {
          intMatcher("heightCentimetres" EQ 181)
          stringMatcher("firstName" IS "John1")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1")
    }

    @Test
    fun `integers OR strings`() {
      val request = RequestDsl {
        query {
          joinType = OR
          intMatcher("heightCentimetres" EQ 181)
          stringMatcher("firstName" IS "James2")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1", "P2")
    }
  }

  @Nested
  inner class BooleanMatchers {
    @Test
    fun `single true`() {
      val request = RequestDsl {
        query {
          booleanMatcher("recall" IS true)
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P2")
    }

    @Test
    fun `single false`() {
      val request = RequestDsl {
        query {
          booleanMatcher("recall" IS false)
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1", "P3")
    }

    @Test
    fun `booleans with AND`() {
      val request = RequestDsl {
        query {
          booleanMatcher("recall" IS true)
          booleanMatcher("youthOffender" IS false)
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P2")
    }

    @Test
    fun `booleans with OR`() {
      val request = RequestDsl {
        query {
          joinType = OR
          booleanMatcher("recall" IS true)
          booleanMatcher("youthOffender" IS true)
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1", "P2", "P3")
    }

    @Test
    fun `booleans AND strings AND integers`() {
      val request = RequestDsl {
        query {
          booleanMatcher("recall" IS false)
          stringMatcher("firstName" IS "Jeff3")
          intMatcher("heightCentimetres" EQ 183)
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P3")
    }

    @Test
    fun `booleans OR strings OR integers`() {
      val request = RequestDsl {
        query {
          joinType = OR
          booleanMatcher("recall" IS true)
          stringMatcher("firstName" IS "John1")
          intMatcher("heightCentimetres" EQ 183)
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1", "P2", "P3")
    }
  }

  @Nested
  inner class DateTimeMatchers {

    @Test
    fun `single greater than`() {
      val request = RequestDsl {
        query {
          dateTimeMatcher("currentIncentive.dateTime" GT now.minusDays(1))
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P3")
    }

    @Test
    fun `single less than`() {
      val request = RequestDsl {
        query {
          dateTimeMatcher("currentIncentive.dateTime" LT now.minusDays(2))
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1")
    }

    @Test
    fun `single between`() {
      val request = RequestDsl {
        query {
          dateTimeMatcher("currentIncentive.dateTime" GT now.minusDays(2) AND_LT now.minusDays(1))
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P2")
    }

    @Test
    fun `AND with greater than and less than`() {
      val request = RequestDsl {
        query {
          dateTimeMatcher("currentIncentive.dateTime" GT now.minusDays(2))
          dateTimeMatcher("currentIncentive.dateTime" LT now.minusDays(1))
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P2")
    }

    @Test
    fun `OR with less than and greater than`() {
      val request = RequestDsl {
        query {
          joinType = OR
          dateTimeMatcher("currentIncentive.dateTime" LT now.minusDays(2))
          dateTimeMatcher("currentIncentive.dateTime" GT now.minusDays(1))
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1", "P3")
    }

    @Test
    fun `AND with a string matcher`() {
      val request = RequestDsl {
        query {
          dateTimeMatcher("currentIncentive.dateTime" GT now.minusDays(2))
          stringMatcher("firstName" IS "Jeff3")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P3")
    }

    @Test
    fun `OR with a string matcher`() {
      val request = RequestDsl {
        query {
          joinType = OR
          dateTimeMatcher("currentIncentive.dateTime" GT now.minusDays(2))
          stringMatcher("firstName" IS "James2")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P2", "P3")
    }

    @Test
    fun `AND with an int matcher`() {
      val request = RequestDsl {
        query {
          dateTimeMatcher("currentIncentive.dateTime" GT now.minusDays(2))
          intMatcher("heightCentimetres" EQ 183)
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P3")
    }

    @Test
    fun `OR with an int matcher`() {
      val request = RequestDsl {
        query {
          joinType = OR
          dateTimeMatcher("currentIncentive.dateTime" GT now.minusDays(2))
          intMatcher("heightCentimetres" EQ 182)
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P2", "P3")
    }
  }

  @Nested
  inner class DateMatchers {
    @Test
    fun `single equals`() {
      val request = RequestDsl {
        query {
          dateMatcher("dateOfBirth" EQ "1990-01-01")
        }
      }

      webTestClient.attributeSearch(request)
        .expectPrisoners("P1")
    }

    @Test
    fun `single greater than or equal to`() {
      val request = RequestDsl {
        query {
          dateMatcher("dateOfBirth" GTE "1990-01-03")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P3")
    }

    @Test
    fun `single greater than`() {
      val request = RequestDsl {
        query {
          dateMatcher("dateOfBirth" GT "1990-01-02")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P3")
    }

    @Test
    fun `single less than or equal to`() {
      val request = RequestDsl {
        query {
          dateMatcher("dateOfBirth" LTE "1990-01-01")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1")
    }

    @Test
    fun `single less than`() {
      val request = RequestDsl {
        query {
          dateMatcher("dateOfBirth" LT "1990-01-02")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1")
    }

    @Test
    fun `between inclusive`() {
      val request = RequestDsl {
        query {
          dateMatcher("dateOfBirth" GTE "1990-01-01" AND_LTE "1990-01-02")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1", "P2")
    }

    @Test
    fun `between exclusive`() {
      val request = RequestDsl {
        query {
          dateMatcher("dateOfBirth" GT "1990-01-01" AND_LT "1990-01-03")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P2")
    }

    @Test
    fun `AND with 2 dates`() {
      val request = RequestDsl {
        query {
          dateMatcher("dateOfBirth" GTE "1990-01-01")
          dateMatcher("dateOfBirth" LT "1990-01-02")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1")
    }

    @Test
    fun `OR with 2 dates`() {
      val request = RequestDsl {
        query {
          joinType = OR
          dateMatcher("dateOfBirth" GT "1990-01-01")
          dateMatcher("dateOfBirth" LTE "1990-01-03")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1", "P2", "P3")
    }

    @Test
    fun `dates AND strings`() {
      val request = RequestDsl {
        query {
          dateMatcher("dateOfBirth" EQ "1990-01-01")
          stringMatcher("firstName" IS "John1")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1")
    }

    @Test
    fun `dates OR strings`() {
      val request = RequestDsl {
        query {
          joinType = OR
          dateMatcher("dateOfBirth" EQ "1990-01-01")
          stringMatcher("firstName" IS "James2")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1", "P2")
    }
  }

  @Nested
  inner class SubQueries {

    @Test
    fun `single sub-query`() {
      val request = RequestDsl {
        query {
          subQuery {
            stringMatcher("firstName" IS "John1")
          }
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1")
    }

    @Test
    fun `single sub-query with multiple matchers`() {
      val request = RequestDsl {
        query {
          subQuery {
            stringMatcher("firstName" IS "John1")
            dateMatcher("dateOfBirth" EQ "1990-01-01")
          }
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1")
    }

    @Test
    fun `single sub-query with multiple OR matchers`() {
      val request = RequestDsl {
        query {
          subQuery {
            joinType = OR
            stringMatcher("firstName" IS "John1")
            dateMatcher("dateOfBirth" EQ "1990-01-02")
          }
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1", "P2")
    }

    @Test
    fun `multiple sub-queries with AND`() {
      val request = RequestDsl {
        query {
          subQuery {
            stringMatcher("firstName" IS "John1")
          }
          subQuery {
            dateMatcher("dateOfBirth" EQ "1990-01-01")
          }
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1")
    }

    @Test
    fun `multiple sub-queries with OR`() {
      val request = RequestDsl {
        query {
          joinType = OR
          subQuery {
            stringMatcher("firstName" IS "John1")
          }
          subQuery {
            dateMatcher("dateOfBirth" EQ "1990-01-02")
          }
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1", "P2")
    }

    @Test
    fun `matcher with single sub-query`() {
      val request = RequestDsl {
        query {
          stringMatcher("firstName" IS "John1")
          subQuery {
            dateMatcher("dateOfBirth" EQ "1990-01-01")
          }
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1")
    }

    @Test
    fun `matcher with single sub-query using OR`() {
      val request = RequestDsl {
        query {
          joinType = OR
          stringMatcher("firstName" IS "John1")
          subQuery {
            dateMatcher("dateOfBirth" EQ "1990-01-02")
          }
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1", "P2")
    }

    @Test
    fun `multiple nested sub-queries`() {
      // firstName CONTAINS J AND ((dateOfBirth = 1990-01-01) OR (dateOfBirth = 1990-01-02))
      val request = RequestDsl {
        query {
          joinType = JoinType.AND
          stringMatcher("firstName" CONTAINS "J")
          subQuery {
            joinType = OR
            subQuery {
              dateMatcher("dateOfBirth" EQ "1990-01-01")
            }
            subQuery {
              dateMatcher("dateOfBirth" EQ "1990-01-02")
            }
          }
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1", "P2")
    }
  }

  @Nested
  inner class MultipleQueries {
    @Test
    fun `AND with basic matchers`() {
      val request = RequestDsl {
        query {
          stringMatcher("firstName" IS "John1")
        }
        query {
          dateMatcher("dateOfBirth" EQ "1990-01-01")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1")
    }

    @Test
    fun `OR with basic matchers`() {
      val request = RequestDsl {
        joinType = OR
        query {
          stringMatcher("firstName" IS "John1")
        }
        query {
          stringMatcher("firstName" IS "James2")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1", "P2")
    }

    @Test
    fun `AND with sub-queries`() {
      val request = RequestDsl {
        query {
          subQuery {
            stringMatcher("firstName" IS "John1")
          }
        }
        query {
          subQuery {
            dateMatcher("dateOfBirth" EQ "1990-01-01")
          }
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1")
    }

    @Test
    fun `OR with sub-queries`() {
      val request = RequestDsl {
        joinType = OR
        query {
          subQuery {
            stringMatcher("firstName" IS "John1")
          }
        }
        query {
          subQuery {
            stringMatcher("firstName" IS "James2")
          }
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1", "P2")
    }

    @Test
    fun `with matchers and sub-queries`() {
      val request = RequestDsl {
        joinType = OR
        query {
          stringMatcher("firstName" IS "John1")
          subQuery {
            dateMatcher("dateOfBirth" EQ "1990-01-01")
          }
        }
        query {
          stringMatcher("firstName" IS "James2")
          subQuery {
            dateMatcher("dateOfBirth" EQ "1990-01-02")
          }
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1", "P2")
    }
  }

  @Nested
  inner class BodyParts {
    @Test
    fun `should match tattoo on both body part AND IS comment`() {
      val request = RequestDsl {
        query {
          stringMatcher("tattoos.bodyPart" IS "Arm")
          stringMatcher("tattoos.comment" IS "dragon")
        }
      }

      // importantly this ignores P3 which has tattoos Arm/heart and Shoulder/dragon but NOT Arm/dragon
      webTestClient.attributeSearch(request).expectPrisoners("P1")
    }

    @Test
    fun `should match tattoo on both body part AND CONTAINS comment`() {
      val request = RequestDsl {
        query {
          stringMatcher("tattoos.bodyPart" IS "Arm")
          stringMatcher("tattoos.comment" CONTAINS "dragon")
        }
      }

      // importantly this ignores P3 which has tattoos Arm/heart and Shoulder/dragon but NOT Arm/dragon
      webTestClient.attributeSearch(request).expectPrisoners("P1", "P2")
    }

    @Test
    fun `should match tattoo on either body part or comment in different queries`() {
      val request = RequestDsl {
        query {
          stringMatcher("tattoos.bodyPart" IS "Arm")
        }
        query {
          stringMatcher("tattoos.comment" IS "dragon")
        }
      }

      // importantly this includes P3 because we only check nested objects within the same query
      webTestClient.attributeSearch(request).expectPrisoners("P1", "P3")
    }

    @Test
    fun `should match scar on both body part AND IS comment`() {
      val request = RequestDsl {
        query {
          stringMatcher("scars.bodyPart" IS "Arm")
          stringMatcher("scars.comment" IS "bite")
        }
      }

      // importantly this ignores P3 which has scars Arm/wrist and Shoulder/bite but NOT Arm/bite
      webTestClient.attributeSearch(request).expectPrisoners("P1")
    }

    @Test
    fun `should match scar on both body part AND CONTAINS comment`() {
      val request = RequestDsl {
        query {
          stringMatcher("scars.bodyPart" IS "Arm")
          stringMatcher("scars.comment" CONTAINS "bite")
        }
      }

      // importantly this ignores P3 which has scars Arm/wrist and Shoulder/bite but NOT Arm/bite
      webTestClient.attributeSearch(request).expectPrisoners("P1", "P2")
    }

    @Test
    fun `should match scar on either body part or comment in different queries`() {
      val request = RequestDsl {
        query {
          stringMatcher("scars.bodyPart" IS "Arm")
        }
        query {
          stringMatcher("scars.comment" IS "bite")
        }
      }

      // importantly this includes P3 because we only check nested objects within the same query
      webTestClient.attributeSearch(request).expectPrisoners("P1", "P3")
    }

    @Test
    fun `should match marks on both body part AND IS comment`() {
      val request = RequestDsl {
        query {
          stringMatcher("marks.bodyPart" IS "Arm")
          stringMatcher("marks.comment" IS "birthmark")
        }
      }

      // importantly this ignores P3 which has marks on Arm and Shoulder/birthmark but NOT Arm/birthmark
      webTestClient.attributeSearch(request).expectPrisoners("P1")
    }

    @Test
    fun `should match marks on both body part AND CONTAINS comment`() {
      val request = RequestDsl {
        query {
          stringMatcher("marks.bodyPart" IS "Arm")
          stringMatcher("marks.comment" CONTAINS "birthmark")
        }
      }

      // importantly this ignores P3 which has marks on Arm and Shoulder/birthmark but NOT Arm/birthmark
      webTestClient.attributeSearch(request).expectPrisoners("P1", "P2")
    }

    @Test
    fun `should match marks on either body part or comment in different query`() {
      val request = RequestDsl {
        query {
          stringMatcher("marks.bodyPart" IS "Arm")
        }
        query {
          stringMatcher("marks.comment" IS "birthmark")
        }
      }

      // importantly this includes P3 because we only check nested objects within the same query
      webTestClient.attributeSearch(request).expectPrisoners("P1", "P3")
    }
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
