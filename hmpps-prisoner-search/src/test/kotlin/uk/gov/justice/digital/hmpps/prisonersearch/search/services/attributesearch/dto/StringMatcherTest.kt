package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.dto

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.AttributeSearchException
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.StringMatcher
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.TextCondition

class StringMatcherTest {
  @Test
  fun `should not allow blank value`() {
    val matcher = StringMatcher("firstName", TextCondition.IS, "")

    assertThrows<AttributeSearchException> {
      matcher.validate()
    }.also {
      assertThat(it.message).contains("firstName").contains("blank")
    }
  }
}
