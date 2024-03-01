package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.dto

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.AttributeSearchException
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.Query

class QueryTest {
  @Test
  fun `should not allow empty queries`() {
    val query = Query()

    assertThrows<AttributeSearchException> {
      query.validate()
    }.also {
      assertThat(it.message).contains("empty")
    }
  }
}
