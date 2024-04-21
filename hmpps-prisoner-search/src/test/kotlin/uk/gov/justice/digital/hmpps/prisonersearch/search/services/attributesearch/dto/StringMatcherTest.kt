package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.dto

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.AttributeSearchException
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.StringCondition
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.StringMatcher

class StringMatcherTest {
  @Test
  fun `should not allow blank value`() {
    val matcher = StringMatcher("firstName", StringCondition.IS, "")

    assertThrows<AttributeSearchException> {
      matcher.validate()
    }.also {
      assertThat(it.message).contains("firstName").contains("blank")
    }
  }

  @Test
  fun `should not allow empty list with IN`() {
    val matcher = StringMatcher("firstName", StringCondition.IN, "")

    assertThrows<AttributeSearchException> {
      matcher.validate()
    }.also {
      assertThat(it.message).contains("firstName").contains("empty list")
    }
  }

  @Test
  fun `should not allow multiple empty values with IN`() {
    val matcher = StringMatcher("firstName", StringCondition.IN, ",")

    assertThrows<AttributeSearchException> {
      matcher.validate()
    }.also {
      assertThat(it.message).contains("firstName").contains("empty list")
    }
  }
}
