package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.dto

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.AttributeSearchException
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.JoinType
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.Matchers

class MatchersTest {
  @Test
  fun `should not allow empty matcher`() {
    val matchers = Matchers(JoinType.AND)

    assertThrows<AttributeSearchException> {
      matchers.validate()
    }.also {
      assertThat(it.message).contains("empty")
    }
  }
}
