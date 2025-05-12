package uk.gov.justice.digital.hmpps.prisonersearch.common.model

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

data class PersonalCareNeed(
  @Schema(description = "Problem Type, from reference data with domain 'HEALTH'", example = "MATSTAT")
  val problemType: String? = null,
  @Schema(description = "Problem Code, from reference data with domain 'HEALTH_PBLM'", example = "ACCU9")
  val problemCode: String? = null,
  @Schema(description = "Problem Status, from reference data with domain 'HEALTH_STS'", allowableValues = ["ON", "I", "EBS"])
  val problemStatus: String? = null,
  @Schema(description = "Problem Description")
  val problemDescription: String? = null,
  @Schema(description = "Comment")
  val commentText: String? = null,
  @Schema(description = "Start Date", example = "2020-06-21")
  val startDate: LocalDate? = null,
  @Schema(description = "End Date", example = "2025-05-11")
  val endDate: LocalDate? = null,
)
