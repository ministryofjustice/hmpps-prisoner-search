package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.dto

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.AttributeSearchException
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.AttributeSearchRequest
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.Query
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.StringCondition.IS
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.StringMatcher

class AttributeSearchRequestTest {

  @Test
  fun `should validate queries`() {
    val request = AttributeSearchRequest()

    assertThrows<AttributeSearchException> {
      request.validate(emptyMap())
    }.also {
      assertThat(it.message).contains("empty")
    }
  }

  @Test
  fun `should validate type matchers`() {
    val request = AttributeSearchRequest(
      queries = listOf(
        Query(
          matchers = listOf(StringMatcher("missingAttribute", IS, "value")),
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
