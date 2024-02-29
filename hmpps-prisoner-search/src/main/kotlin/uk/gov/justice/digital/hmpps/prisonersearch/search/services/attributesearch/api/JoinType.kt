package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "The type of join to use when combining matchers or subQueries. Defaults to AND if not included in the request.", example = "AND", defaultValue = "AND")
enum class JoinType {
  AND,
  OR,
}
