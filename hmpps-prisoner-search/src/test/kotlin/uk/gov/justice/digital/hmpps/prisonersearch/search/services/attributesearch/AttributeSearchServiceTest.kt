package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.AttributeSearchRequest
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.BooleanMatcher
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.DateMatcher
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.DateTimeMatcher
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.IntMatcher
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.JoinType
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.Matchers
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.StringMatcher
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.TextCondition
import java.time.LocalDate
import java.time.LocalDateTime

class AttributeSearchServiceTest {

  private val service = AttributeSearchService(getAttributes(Prisoner::class))

  @Nested
  inner class Matchers {
    @Test
    fun `should not allow child matchers with no contents`() {
      val request = AttributeSearchRequest(
        listOf(
          Matchers(
            JoinType.AND,
            children = listOf(
              Matchers(JoinType.AND),
            ),

          ),
        ),
      )

      assertThrows<AttributeSearchException> {
        service.search(request)
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
        service.search(request)
      }.also {
        assertThat(it.message).contains("empty")
      }
    }

    @Test
    fun `should validate attributes in nested matchers`() {
      val request = AttributeSearchRequest(
        listOf(
          Matchers(
            JoinType.AND,
            children = listOf(
              Matchers(
                JoinType.AND,
                stringMatchers = listOf(
                  StringMatcher("firstName", TextCondition.IS, ""),
                ),
              ),
            ),
          ),
        ),
      )

      assertThrows<AttributeSearchException> {
        service.search(request)
      }.also {
        assertThat(it.message).contains("firstName").contains("blank")
      }
    }

    @Test
    fun `should validate attributes from a deep nested matcher`() {
      val request = AttributeSearchRequest(
        listOf(
          Matchers(
            JoinType.AND,
            children = listOf(
              Matchers(
                JoinType.AND,
                children = listOf(
                  Matchers(
                    JoinType.AND,
                    stringMatchers = listOf(StringMatcher("firstName", TextCondition.IS, "")),
                  ),
                ),
              ),
            ),
          ),
        ),
      )

      assertThrows<AttributeSearchException> {
        service.search(request)
      }.also {
        assertThat(it.message).contains("firstName").contains("blank")
      }
    }
  }

  @Nested
  inner class Attributes {
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
        service.search(request)
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
        service.search(request)
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
        service.search(request)
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
        service.search(request)
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
        service.search(request)
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
        service.search(request)
      }.also {
        assertThat(it.message).contains("currentIncentive.level.unknown")
      }
    }
  }

  @Nested
  inner class AttributeTypes {
    @Test
    fun `should not allow a String matcher for non-string attributes`() {
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
        service.search(request)
      }.also {
        assertThat(it.message).contains("heightCentimetres").contains("String")
      }
    }

    @Test
    fun `should not allow a Boolean matcher for non-boolean attributes`() {
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
        service.search(request)
      }.also {
        assertThat(it.message).contains("firstName").contains("Boolean")
      }
    }

    @Test
    fun `should not allow an Integer matcher for non-integer attributes`() {
      val request = AttributeSearchRequest(
        listOf(
          Matchers(
            JoinType.AND,
            intMatchers = listOf(
              IntMatcher("firstName", minValue = 150),
            ),
          ),
        ),
      )

      assertThrows<AttributeSearchException> {
        service.search(request)
      }.also {
        assertThat(it.message).contains("firstName").contains("Int")
      }
    }

    @Test
    fun `should not allow Date matcher for non-date attributes`() {
      val today = LocalDate.now()
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
        service.search(request)
      }.also {
        assertThat(it.message).contains("firstName").contains("LocalDate")
      }
    }

    @Test
    fun `should not allow DateTime matcher for non-datetime attributes`() {
      val now = LocalDateTime.now()
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
        service.search(request)
      }.also {
        assertThat(it.message).contains("firstName").contains("LocalDateTime")
      }
    }
  }
}
