package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.dto

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.AttributeSearchException
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.AttributeSearchRequest

class AttributeSearchRequestTest {
  @Test
  fun `should not allow zero matchers`() {
    val request = AttributeSearchRequest(emptyList())

    assertThrows<AttributeSearchException> {
      request.validate()
    }.also {
      assertThat(it.message).contains("one matcher")
    }
  }
}
