package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.dto

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.AttributeSearchException
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.DateMatcher
import java.time.LocalDate

class DateMatcherTest {

  private val today = LocalDate.now()
  private val yesterday = today.minusDays(1)
  private val tomorrow = today.plusDays(1)

  @Test
  fun `should not allow missing min and max values`() {
    val matcher = DateMatcher("releaseDate")

    assertThrows<AttributeSearchException> {
      matcher.validate()
    }.also {
      assertThat(it.message).contains("releaseDate").contains("must have at least 1 min or max value")
    }
  }

  @Test
  fun `should not allow max less than min`() {
    val matcher = DateMatcher("releaseDate", minValue = today, maxValue = yesterday)

    assertThrows<AttributeSearchException> {
      matcher.validate()
    }.also {
      assertThat(it.message).contains("releaseDate").contains("max value $yesterday less than min value $today")
    }
  }

  @Test
  fun `should allow min equal to max if both inclusive`() {
    val matcher = DateMatcher("releaseDate", minValue = today, minInclusive = true, maxValue = today, maxInclusive = true)

    assertDoesNotThrow {
      matcher.validate()
    }
  }

  @Test
  fun `should not allow max less than min when exclusive`() {
    // this means today < releaseDate < tomorrow which is impossible
    val matcher = DateMatcher("releaseDate", minValue = today, minInclusive = false, maxValue = tomorrow, maxInclusive = false)

    assertThrows<AttributeSearchException> {
      matcher.validate()
    }.also {
      assertThat(it.message).contains("releaseDate").contains("max value $today less than min value $tomorrow")
    }
  }
}
