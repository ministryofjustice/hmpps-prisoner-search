package uk.gov.justice.digital.hmpps.prisonersearch.search.services

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.AttributeSearchException
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.AttributeSearchRequest
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.AttributeSearchService
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.BooleanMatcher
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.DateMatcher
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.DateTimeMatcher
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.IntegerMatcher
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.JoinType
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.Matchers
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.TextCondition
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.StringMatcher
import java.time.LocalDate
import java.time.LocalDateTime

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
        val request = AttributeSearchRequest(listOf(Matchers(JoinType.AND)))

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
            Matchers(
              JoinType.AND,
              children = listOf(Matchers(JoinType.AND)),
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
            Matchers(
              JoinType.AND,
              children = listOf(
                Matchers(
                  JoinType.AND,
                  children = listOf(
                    Matchers(JoinType.AND),
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
            Matchers(
              JoinType.AND,
              stringMatchers = listOf(
                StringMatcher("firstName", TextCondition.IS, "value"),
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
            Matchers(
              JoinType.AND,
              stringMatchers = listOf(
                StringMatcher("unknownAttribute", TextCondition.IS, "value"),
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
            Matchers(
              JoinType.AND,
              stringMatchers = listOf(
                StringMatcher("aliases.firstName", TextCondition.IS, "value"),
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
            Matchers(
              JoinType.AND,
              stringMatchers = listOf(
                StringMatcher("aliases.unknown", TextCondition.IS, "value"),
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
            Matchers(
              JoinType.AND,
              stringMatchers = listOf(
                StringMatcher("currentIncentive.level.code", TextCondition.IS, "value"),
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
            Matchers(
              JoinType.AND,
              stringMatchers = listOf(
                StringMatcher("currentIncentive.level.unknown", TextCondition.IS, "value"),
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
    inner class StringMatchers {
      @Test
      fun `should not allow attributes of the wrong type`() {
        val request = AttributeSearchRequest(
          listOf(
            Matchers(
              JoinType.AND,
              stringMatchers = listOf(
                StringMatcher("heightCentimetres", TextCondition.IS, "value"),
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
            Matchers(
              JoinType.AND,
              stringMatchers = listOf(
                StringMatcher("firstName", TextCondition.IS, ""),
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
            Matchers(
              JoinType.AND,
              stringMatchers = listOf(
                StringMatcher("aliases.firstName", TextCondition.IS, ""),
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
            Matchers(
              JoinType.AND,
              stringMatchers = listOf(
                StringMatcher("currentIncentive.level.code", TextCondition.IS, ""),
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
    inner class BooleanMatchers {
      @Test
      fun `should not allow attributes of the wrong type`() {
        val request = AttributeSearchRequest(
          listOf(
            Matchers(
              JoinType.AND,
              booleanMatchers = listOf(
                BooleanMatcher("firstName", true),
              ),
            ),
          ),
        )

        assertThrows<AttributeSearchException> {
          service.validate(request)
        }.also {
          assertThat(it.message).contains("firstName").contains("boolean attribute")
        }
      }

      @Test
      fun `should allow boolean attributes`() {
        val request = AttributeSearchRequest(
          listOf(
            Matchers(
              JoinType.AND,
              booleanMatchers = listOf(
                BooleanMatcher("recall", true),
              ),
            ),
          ),
        )

        assertDoesNotThrow {
          service.validate(request)
        }
      }
    }

    @Nested
    inner class IntegerMatchers {
      @Test
      fun `should not allow attributes of the wrong type`() {
        val request = AttributeSearchRequest(
          listOf(
            Matchers(
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
            Matchers(
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
            Matchers(
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
            Matchers(
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
            Matchers(
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

    @Nested
    inner class DateMatchers {
      private val today = LocalDate.now()
      private val yesterday = today.minusDays(1)
      private val tomorrow = today.plusDays(1)

      @Test
      fun `should not allow attributes of the wrong type`() {
        val request = AttributeSearchRequest(
          listOf(
            Matchers(
              JoinType.AND,
              dateMatchers = listOf(
                DateMatcher("firstName", minValue = today),
              ),
            ),
          ),
        )

        assertThrows<AttributeSearchException> {
          service.validate(request)
        }.also {
          assertThat(it.message).contains("firstName").contains("date attribute")
        }
      }

      @Test
      fun `should not allow missing min and max values`() {
        val request = AttributeSearchRequest(
          listOf(
            Matchers(
              JoinType.AND,
              dateMatchers = listOf(
                DateMatcher("releaseDate"),
              ),
            ),
          ),
        )

        assertThrows<AttributeSearchException> {
          service.validate(request)
        }.also {
          assertThat(it.message).contains("releaseDate").contains("must have at least 1 min or max value")
        }
      }

      @Test
      fun `should not allow max less than min`() {
        val request = AttributeSearchRequest(
          listOf(
            Matchers(
              JoinType.AND,
              dateMatchers = listOf(
                DateMatcher("releaseDate", minValue = today, maxValue = yesterday),
              ),
            ),
          ),
        )

        assertThrows<AttributeSearchException> {
          service.validate(request)
        }.also {
          assertThat(it.message).contains("releaseDate").contains("max value $yesterday less than min value $today")
        }
      }

      @Test
      fun `should allow min equal to max if both inclusive`() {
        val request = AttributeSearchRequest(
          listOf(
            Matchers(
              JoinType.AND,
              dateMatchers = listOf(
                DateMatcher("releaseDate", minValue = today, minInclusive = true, maxValue = today, maxInclusive = true),
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
            Matchers(
              JoinType.AND,
              dateMatchers = listOf(
                // this means today < releaseDate < tomorrow which is impossible
                DateMatcher("releaseDate", minValue = today, minInclusive = false, maxValue = tomorrow, maxInclusive = false),
              ),
            ),
          ),
        )

        assertThrows<AttributeSearchException> {
          service.validate(request)
        }.also {
          assertThat(it.message).contains("releaseDate").contains("max value $today less than min value $tomorrow")
        }
      }
    }

    @Nested
    inner class DateTimeMatchers {
      private val now = LocalDateTime.now()

      @Test
      fun `should not allow attributes of the wrong type`() {
        val request = AttributeSearchRequest(
          listOf(
            Matchers(
              JoinType.AND,
              dateTimeMatchers = listOf(
                DateTimeMatcher("firstName", minValue = now.minusDays(7)),
              ),
            ),
          ),
        )

        assertThrows<AttributeSearchException> {
          service.validate(request)
        }.also {
          assertThat(it.message).contains("firstName").contains("datetime attribute")
        }
      }

      @Test
      fun `should not allow missing min and max values`() {
        val request = AttributeSearchRequest(
          listOf(
            Matchers(
              JoinType.AND,
              dateTimeMatchers = listOf(
                DateTimeMatcher("currentIncentive.dateTime"),
              ),
            ),
          ),
        )

        assertThrows<AttributeSearchException> {
          service.validate(request)
        }.also {
          assertThat(it.message).contains("currentIncentive.dateTime").contains("must have at least 1 min or max value")
        }
      }

      @Test
      fun `should not allow max less than min`() {
        val min = now
        val max = now.minusSeconds(1)
        val request = AttributeSearchRequest(
          listOf(
            Matchers(
              JoinType.AND,
              dateTimeMatchers = listOf(
                DateTimeMatcher("currentIncentive.dateTime", minValue = min, maxValue = max),
              ),
            ),
          ),
        )

        assertThrows<AttributeSearchException> {
          service.validate(request)
        }.also {
          assertThat(it.message).contains("currentIncentive.dateTime").contains("max value $max less than min value $min")
        }
      }

      @Test
      fun `should allow min equal to max`() {
        val request = AttributeSearchRequest(
          listOf(
            Matchers(
              JoinType.AND,
              dateTimeMatchers = listOf(
                DateTimeMatcher("currentIncentive.dateTime", minValue = now, maxValue = now),
              ),
            ),
          ),
        )

        assertDoesNotThrow {
          service.validate(request)
        }
      }
    }
  }
}
