package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.dto

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.AttributeSearchException
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.AttributeSearchRequest
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.JoinType
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.Matchers
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.StringMatcher
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.TextCondition

class AttributeSearchRequestTest {
  @Test
  fun `should not allow zero matchers`() {
    val request = AttributeSearchRequest(emptyList())

    assertThrows<AttributeSearchException> {
      request.validate(emptyMap())
    }.also {
      assertThat(it.message).contains("one matcher")
    }
  }

  @Test
  fun `should validate matchers`() {
    val request = AttributeSearchRequest(listOf(Matchers(JoinType.AND)))

    assertThrows<AttributeSearchException> {
      request.validate(emptyMap())
    }.also {
      assertThat(it.message).contains("empty")
    }
  }

  @Test
  fun `should validate type matchers`() {
    val request = AttributeSearchRequest(
      listOf(
        Matchers(
          JoinType.AND,
          stringMatchers = listOf(StringMatcher("missingAttribute", TextCondition.IS, "value")),
        ),
      ),
    )

    assertThrows<AttributeSearchException> {
      request.validate(emptyMap())
    }.also {
      assertThat(it.message).contains("missingAttribute")
    }
  }
}
