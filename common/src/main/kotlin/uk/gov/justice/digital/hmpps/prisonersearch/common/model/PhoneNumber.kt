package uk.gov.justice.digital.hmpps.prisonersearch.common.model

import io.swagger.v3.oas.annotations.media.Schema

data class PhoneNumber(
  @Schema(description = "The type of the phone number. Will never be null.", example = "HOME, MOB")
  val type: String? = null,

  @Schema(description = "The phone number. Numeric characters only (no whitespace). Will never be null.", example = "01141234567")
  val number: String? = null,
)
