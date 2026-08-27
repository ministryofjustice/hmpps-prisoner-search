package uk.gov.justice.digital.hmpps.prisonersearch.common.model

import io.swagger.v3.oas.annotations.media.Schema

data class MainOffence(
  @Schema(description = "Offence code, from the charge held in NOMIS", example = "RR84070")
  val offenceCode: String? = null,
  @Schema(description = "Description of the offence", example = "Actual bodily harm")
  val offenceDescription: String? = null,
)
