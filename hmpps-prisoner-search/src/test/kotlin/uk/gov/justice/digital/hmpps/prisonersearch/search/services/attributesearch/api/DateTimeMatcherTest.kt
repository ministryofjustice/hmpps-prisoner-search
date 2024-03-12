package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opensearch.index.query.RangeQueryBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.Attribute
import java.time.LocalDateTime

class DateTimeMatcherTest {
  @Test
  fun `should not truncate seconds if they are 00`() {
    val dateTimeMatcher = DateTimeMatcher(
      attribute = "currentIncentive.dateTime",
      minValue = LocalDateTime.parse("2024-01-01T09:00:00"),
      maxValue = LocalDateTime.parse("2024-01-31T21:00:00.123"),
    )

    val attributes = mapOf("currentIncentive.dateTime" to Attribute(LocalDateTime::class, "currentIncentive.dateTime"))
    val query = dateTimeMatcher.buildQuery(attributes) as RangeQueryBuilder

    assertThat(query.from()).isEqualTo("2024-01-01T09:00:00")
    assertThat(query.to()).isEqualTo("2024-01-31T21:00:00")
  }
}
