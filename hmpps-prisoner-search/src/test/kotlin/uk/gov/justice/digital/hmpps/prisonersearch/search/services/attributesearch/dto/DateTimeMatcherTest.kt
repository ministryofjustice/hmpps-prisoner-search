package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.dto

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.AttributeSearchException
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.DateTimeMatcher
import java.time.LocalDateTime

class DateTimeMatcherTest {

  private val now = LocalDateTime.now()

  @Test
  fun `should not allow missing min and max values`() {
    val matcher = DateTimeMatcher("currentIncentive.dateTime")

    assertThrows<AttributeSearchException> {
      matcher.validate()
    }.also {
      assertThat(it.message).contains("currentIncentive.dateTime").contains("must have at least 1 min or max value")
    }
  }

  @Test
  fun `should not allow max less than min`() {
    val min = now
    val max = now.minusSeconds(1)
    val matcher = DateTimeMatcher("currentIncentive.dateTime", minValue = min, maxValue = max)

    assertThrows<AttributeSearchException> {
      matcher.validate()
    }.also {
      assertThat(it.message).contains("currentIncentive.dateTime").contains("max value $max less than min value $min")
    }
  }

  @Test
  fun `should allow min equal to max`() {
    val matcher = DateTimeMatcher("currentIncentive.dateTime", minValue = now, maxValue = now)

    assertDoesNotThrow {
      matcher.validate()
    }
  }
}
