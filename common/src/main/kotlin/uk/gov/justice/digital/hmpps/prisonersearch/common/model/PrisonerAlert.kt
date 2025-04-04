package uk.gov.justice.digital.hmpps.prisonersearch.common.model

import io.swagger.v3.oas.annotations.media.Schema

data class PrisonerAlert(
  @Schema(description = "Alert Type", example = "H")
  val alertType: String? = null,
  @Schema(description = "Alert Code", example = "HA")
  val alertCode: String? = null,
  @Schema(description = "Active", example = "true")
  val active: Boolean? = null,
  @Schema(description = "Expired", example = "true")
  val expired: Boolean? = null,
)
