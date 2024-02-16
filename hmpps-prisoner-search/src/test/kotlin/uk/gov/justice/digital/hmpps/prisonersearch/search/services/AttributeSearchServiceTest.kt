package uk.gov.justice.digital.hmpps.prisonersearch.search.services

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.dto.AttributeSearchRequest
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.dto.IntegerMatcher
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.dto.JoinType
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.dto.Matcher
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.dto.TextCondition
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.dto.TextMatcher

class AttributeSearchServiceTest {

  private val service = AttributeSearchService()

  @Nested
  inner class Validate {

    @Nested
    inner class Matchers {
      @Test
      fun `should not allow zero matchers`() {
        val request = AttributeSearchRequest(emptyList())

        assertThrows<AttributeSearchException> {
          service.validate(request)
        }.also {
          assertThat(it.message).contains("one matcher")
        }
      }

      @Test
      fun `should not allow a matcher with no contents`() {
        val request = AttributeSearchRequest(listOf(Matcher(JoinType.AND)))

        assertThrows<AttributeSearchException> {
          service.validate(request)
        }.also {
          assertThat(it.message).contains("empty")
        }
      }

      @Test
      fun `should not allow child matchers with no contents`() {
        val request = AttributeSearchRequest(
          listOf(
            Matcher(
              JoinType.AND,
              children = listOf(Matcher(JoinType.AND)),
            ),
          ),
        )

        assertThrows<AttributeSearchException> {
          service.validate(request)
        }.also {
          assertThat(it.message).contains("empty")
        }
      }

      @Test
      fun `should not allow deep nested child matchers with no contents`() {
        val request = AttributeSearchRequest(
          listOf(
            Matcher(
              JoinType.AND,
              children = listOf(
                Matcher(
                  JoinType.AND,
                  children = listOf(
                    Matcher(JoinType.AND),
                  ),
                ),
              ),
            ),
          ),
        )

        assertThrows<AttributeSearchException> {
          service.validate(request)
        }.also {
          assertThat(it.message).contains("empty")
        }
      }
    }

    @Nested
    inner class ValidAttributes {
      @Test
      fun `should allow simple attribute`() {
        val request = AttributeSearchRequest(
          listOf(
            Matcher(
              JoinType.AND,
              textMatchers = listOf(
                TextMatcher("firstName", TextCondition.IS, "value"),
              ),
            ),
          ),
        )

        assertDoesNotThrow {
          service.validate(request)
        }
      }

      @Test
      fun `should not allow unknown attributes`() {
        val request = AttributeSearchRequest(
          listOf(
            Matcher(
              JoinType.AND,
              textMatchers = listOf(
                TextMatcher("unknownAttribute", TextCondition.IS, "value"),
              ),
            ),
          ),
        )

        assertThrows<AttributeSearchException> {
          service.validate(request)
        }.also {
          assertThat(it.message).contains("unknownAttribute")
        }
      }

      @Test
      fun `should allow attributes in lists of objects`() {
        val request = AttributeSearchRequest(
          listOf(
            Matcher(
              JoinType.AND,
              textMatchers = listOf(
                TextMatcher("aliases.firstName", TextCondition.IS, "value"),
              ),
            ),
          ),
        )

        assertDoesNotThrow {
          service.validate(request)
        }
      }

      @Test
      fun `should not allow attributes from lists that don't exist`() {
        val request = AttributeSearchRequest(
          listOf(
            Matcher(
              JoinType.AND,
              textMatchers = listOf(
                TextMatcher("aliases.unknown", TextCondition.IS, "value"),
              ),
            ),
          ),
        )

        assertThrows<AttributeSearchException> {
          service.validate(request)
        }.also {
          assertThat(it.message).contains("aliases.unknown")
        }
      }

      @Test
      fun `should allow attributes in nested objects`() {
        val request = AttributeSearchRequest(
          listOf(
            Matcher(
              JoinType.AND,
              textMatchers = listOf(
                TextMatcher("currentIncentive.level.code", TextCondition.IS, "value"),
              ),
            ),
          ),
        )

        assertDoesNotThrow {
          service.validate(request)
        }
      }

      @Test
      fun `should not allow attributes from nested objects that don't exist`() {
        val request = AttributeSearchRequest(
          listOf(
            Matcher(
              JoinType.AND,
              textMatchers = listOf(
                TextMatcher("currentIncentive.level.unknown", TextCondition.IS, "value"),
              ),
            ),
          ),
        )

        assertThrows<AttributeSearchException> {
          service.validate(request)
        }.also {
          assertThat(it.message).contains("currentIncentive.level.unknown")
        }
      }
    }

    @Nested
    inner class TextMatchers {
      @Test
      fun `should not allow attributes of the wrong type`() {
        val request = AttributeSearchRequest(
          listOf(
            Matcher(
              JoinType.AND,
              textMatchers = listOf(
                TextMatcher("heightCentimetres", TextCondition.IS, "value"),
              ),
            ),
          ),
        )

        assertThrows<AttributeSearchException> {
          service.validate(request)
        }.also {
          assertThat(it.message).contains("heightCentimetres").contains("text attribute")
        }
      }

      @Test
      fun `should not allow blank value`() {
        val request = AttributeSearchRequest(
          listOf(
            Matcher(
              JoinType.AND,
              textMatchers = listOf(
                TextMatcher("firstName", TextCondition.IS, ""),
              ),
            ),
          ),
        )

        assertThrows<AttributeSearchException> {
          service.validate(request)
        }.also {
          assertThat(it.message).contains("firstName").contains("blank")
        }
      }

      @Test
      fun `should not allow blank value in a list of objects`() {
        val request = AttributeSearchRequest(
          listOf(
            Matcher(
              JoinType.AND,
              textMatchers = listOf(
                TextMatcher("aliases.firstName", TextCondition.IS, ""),
              ),
            ),
          ),
        )

        assertThrows<AttributeSearchException> {
          service.validate(request)
        }.also {
          assertThat(it.message).contains("aliases.firstName").contains("blank")
        }
      }

      @Test
      fun `should not allow blank value in a nested object`() {
        val request = AttributeSearchRequest(
          listOf(
            Matcher(
              JoinType.AND,
              textMatchers = listOf(
                TextMatcher("currentIncentive.level.code", TextCondition.IS, ""),
              ),
            ),
          ),
        )

        assertThrows<AttributeSearchException> {
          service.validate(request)
        }.also {
          assertThat(it.message).contains("currentIncentive.level.code").contains("blank")
        }
      }
    }

    @Nested
    inner class IntegerMatchers {
      @Test
      fun `should not allow attributes of the wrong type`() {
        val request = AttributeSearchRequest(
          listOf(
            Matcher(
              JoinType.AND,
              integerMatchers = listOf(
                IntegerMatcher("firstName", minValue = 150),
              ),
            ),
          ),
        )

        assertThrows<AttributeSearchException> {
          service.validate(request)
        }.also {
          assertThat(it.message).contains("firstName").contains("integer attribute")
        }
      }

      @Test
      fun `should not allow missing min and max values`() {
        val request = AttributeSearchRequest(
          listOf(
            Matcher(
              JoinType.AND,
              integerMatchers = listOf(
                IntegerMatcher("heightCentimetres"),
              ),
            ),
          ),
        )

        assertThrows<AttributeSearchException> {
          service.validate(request)
        }.also {
          assertThat(it.message).contains("heightCentimetres").contains("must have at least 1 min or max value")
        }
      }

      @Test
      fun `should not allow max less than min`() {
        val request = AttributeSearchRequest(
          listOf(
            Matcher(
              JoinType.AND,
              integerMatchers = listOf(
                IntegerMatcher("heightCentimetres", minValue = 150, maxValue = 149),
              ),
            ),
          ),
        )

        assertThrows<AttributeSearchException> {
          service.validate(request)
        }.also {
          assertThat(it.message).contains("heightCentimetres").contains("max value 149 less than min value 150")
        }
      }

      @Test
      fun `should allow min equal to max if both inclusive`() {
        val request = AttributeSearchRequest(
          listOf(
            Matcher(
              JoinType.AND,
              integerMatchers = listOf(
                IntegerMatcher("heightCentimetres", minValue = 150, minInclusive = true, maxValue = 150, maxInclusive = true),
              ),
            ),
          ),
        )

        assertDoesNotThrow {
          service.validate(request)
        }
      }

      @Test
      fun `should not allow max less than min when exclusive`() {
        val request = AttributeSearchRequest(
          listOf(
            Matcher(
              JoinType.AND,
              integerMatchers = listOf(
                // this means 150 < heightCentimetres < 151 which is impossible
                IntegerMatcher("heightCentimetres", minValue = 150, minInclusive = false, maxValue = 151, maxInclusive = false),
              ),
            ),
          ),
        )

        assertThrows<AttributeSearchException> {
          service.validate(request)
        }.also {
          assertThat(it.message).contains("heightCentimetres").contains("max value 150 less than min value 151")
        }
      }
    }
  }
}
