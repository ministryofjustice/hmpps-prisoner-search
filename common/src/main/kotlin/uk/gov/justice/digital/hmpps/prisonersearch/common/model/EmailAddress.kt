package uk.gov.justice.digital.hmpps.prisonersearch.common.model

import io.swagger.v3.oas.annotations.media.Schema

data class EmailAddress(
  @Schema(description = "The email address. Will never be null.", example = "john.smith@gmail.com")
  val email: String? = null,
)
