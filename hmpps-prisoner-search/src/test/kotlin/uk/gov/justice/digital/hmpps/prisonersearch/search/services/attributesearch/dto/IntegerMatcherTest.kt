package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.dto

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.AttributeSearchException
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.IntegerMatcher

class IntegerMatcherTest {
  @Test
  fun `should not allow missing min and max values`() {
    val matcher = IntegerMatcher("heightCentimetres")

    assertThrows<AttributeSearchException> {
      matcher.validate()
    }.also {
      assertThat(it.message).contains("heightCentimetres").contains("must have at least 1 min or max value")
    }
  }

  @Test
  fun `should not allow max less than min`() {
    val matcher = IntegerMatcher("heightCentimetres", minValue = 150, maxValue = 149)

    assertThrows<AttributeSearchException> {
      matcher.validate()
    }.also {
      assertThat(it.message).contains("heightCentimetres").contains("max value 149 less than min value 150")
    }
  }

  @Test
  fun `should allow min equal to max if both inclusive`() {
    val matcher = IntegerMatcher("heightCentimetres", minValue = 150, minInclusive = true, maxValue = 150, maxInclusive = true)

    assertDoesNotThrow {
      matcher.validate()
    }
  }

  @Test
  fun `should not allow max less than min when exclusive`() {
    // this means 150 < heightCentimetres < 151 which is impossible
    val matcher = IntegerMatcher("heightCentimetres", minValue = 150, minInclusive = false, maxValue = 151, maxInclusive = false)

    assertThrows<AttributeSearchException> {
      matcher.validate()
    }.also {
      assertThat(it.message).contains("heightCentimetres").contains("max value 150 less than min value 151")
    }
  }
}
