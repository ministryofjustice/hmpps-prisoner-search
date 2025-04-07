package uk.gov.justice.digital.hmpps.prisonersearch.common.model

import io.swagger.v3.oas.annotations.media.Schema

data class PrisonerAlert(
  @Schema(description = "Alert Type. Will never be null.", example = "H")
  val alertType: String? = null,
  @Schema(description = "Alert Code. Will never be null.", example = "HA")
  val alertCode: String? = null,
  @Schema(description = "Active. Will never be null.", example = "true")
  val active: Boolean? = null,
  @Schema(description = "Expired. Will never be null.", example = "true")
  val expired: Boolean? = null,
)
