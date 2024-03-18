package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
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
      physicalMarks = PhysicalMarkBuilder(tattoo = listOf(BodyPartBuilder(bodyPart = "Arm", comment = "dragon"))),
    ),
    PrisonerBuilder(
      prisonerNumber = "P2",
      firstName = "John2",
      dateOfBirth = "1990-01-02",
      heightCentimetres = 182,
      recall = true,
      currentIncentive = IncentiveLevelBuilder(levelDescription = "Incentive level 2", dateTime = now.minusDays(1).minusHours(1)),
      alertCodes = listOf("AT1" to "AC2", "AT2" to "AC2"),
      profileInformation = ProfileInformationBuilder(youthOffender = false),
      physicalMarks = PhysicalMarkBuilder(tattoo = listOf(BodyPartBuilder(bodyPart = "Arm", comment = "dragon/skull"))),
    ),
    PrisonerBuilder(
      prisonerNumber = "P3",
      firstName = "John3",
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
      ),
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
          stringMatcher("firstName" IS_NOT "John2")
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
          stringMatcher("firstName" CONTAINS "John")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1", "P2", "P3")
    }

    @Test
    fun `single CONTAINS uppercase`() {
      val request = RequestDsl {
        query {
          stringMatcher("firstName" CONTAINS "JOHN")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1", "P2", "P3")
    }

    @Test
    fun `single CONTAINS lowercase`() {
      val request = RequestDsl {
        query {
          stringMatcher("firstName" CONTAINS "john")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1", "P2", "P3")
    }

    @Test
    fun `single CONTAINS with single character wildcard`() {
      val request = RequestDsl {
        query {
          stringMatcher("currentIncentive.level.description" CONTAINS "ince?tive level")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1", "P2", "P3")
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
          stringMatcher("currentIncentive.level.description" CONTAINS "ince?tive le?el")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1", "P2", "P3")
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
          stringMatcher("currentIncentive.level.description" CONTAINS "ince*ve level")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1", "P2", "P3")
    }

    @Test
    fun `single CONTAINS with a multiple character wildcard nom atch`() {
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
          stringMatcher("currentIncentive.level.description" CONTAINS "ince*ve l*l")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1", "P2", "P3")
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
    fun `single CONTAINS with partial matches in search term`() {
      // The "John" matches all prisoners, so we're testing that the results must contain the whole search term
      val request = RequestDsl {
        query {
          stringMatcher("firstName" CONTAINS "John1")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1")
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
          stringMatcher("firstName" IS "John1")
          stringMatcher("firstName" CONTAINS "John")
          stringMatcher("firstName" IS_NOT "John2")
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1")
    }

    @Test
    fun `IS, CONTAINS and IS_NOT with OR`() {
      val request = RequestDsl {
        query {
          joinType = OR
          stringMatcher("firstName" IS_NOT "John1")
          stringMatcher("firstName" CONTAINS "John")
          stringMatcher("firstName" IS "John2")
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
          stringMatcher("firstName" IS "John2")
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
          stringMatcher("firstName" IS "John3")
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
          stringMatcher("firstName" IS "John3")
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
          stringMatcher("firstName" IS "John2")
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
          stringMatcher("firstName" IS "John2")
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
      // firstName CONTAINS John AND ((dateOfBirth = 1990-01-01) OR (dateOfBirth = 1990-01-02))
      val request = RequestDsl {
        query {
          joinType = JoinType.AND
          stringMatcher("firstName" CONTAINS "John")
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
          stringMatcher("firstName" IS "John2")
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
            stringMatcher("firstName" IS "John2")
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
          stringMatcher("firstName" IS "John2")
          subQuery {
            dateMatcher("dateOfBirth" EQ "1990-01-02")
          }
        }
      }

      webTestClient.attributeSearch(request).expectPrisoners("P1", "P2")
    }
  }

  @Nested
  @Disabled("These tests currently fail because the tattoos list isn't a Nested field")
  inner class BodyParts {
    @Test
    fun `should match on both body part AND IS comment`() {
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
    fun `should match on both body part AND CONTAINS comment`() {
      val request = RequestDsl {
        query {
          stringMatcher("tattoos.bodyPart" IS "Arm")
          stringMatcher("tattoos.comment" CONTAINS "dragon")
        }
      }

      // importantly this ignores P3 which has tattoos Arm/heart and Shoulder/dragon but NOT Arm/dragon
      webTestClient.attributeSearch(request).expectPrisoners("P1", "P2")
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
