package uk.gov.justice.digital.hmpps.prisonersearch.search.services.dto

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.data.domain.PageRequest

data class PaginationRequest(
  @Schema(description = "The page number required in the paginated response", example = "0")
  val page: Int = 0,

  @Schema(description = "The number of results to return for paginated response", example = "10")
  val size: Int = 10,
) {
  fun toPageable() = PageRequest.of(if (page < 0) 0 else page, if (size < 1) 10 else size)
}
