package uk.gov.justice.digital.hmpps.prisonersearch.common.model

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.data.elasticsearch.annotations.DateFormat
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType
import java.time.LocalDate

/**
 * The prisoner's current CSRA rating as held by the CSRA service (cell-sharing-risk-assessment-api), which
 * owns CSRA and holds ratings recorded in both DPS and NOMIS. Only populated where the indexer takes the CSRA
 * from the CSRA service (csra.from-csra-api), which is being switched on one environment at a time.
 */
data class CurrentCsra(
  @Schema(description = "The rating. Absent when there is no rating.", example = "HIGH_GENERAL", allowableValues = ["HIGH", "HIGH_GENERAL", "HIGH_SPECIFIC", "STANDARD"])
  val rating: String? = null,

  @Schema(description = "Which stage the rating came from. Absent when there is no rating.", example = "FINAL", allowableValues = ["PROVISIONAL", "INTERIM", "FINAL"])
  val ratingStage: String? = null,

  @Schema(description = "The state of the rating", example = "COMPLETE", allowableValues = ["NO_RATING", "IN_PROGRESS", "PROVISIONAL", "COMPLETE"])
  val status: String? = null,

  @Schema(description = "The rating in full, to show as given. \"No rating\" when there is no rating.", example = "High risk – general (interim)")
  val description: String? = null,

  @Schema(description = "The rating in short, to show as given, for example in the prisoner profile header. \"No rating\" when there is no rating.", example = "High – general")
  val shortDescription: String? = null,

  @Field(type = FieldType.Date, format = [DateFormat.date])
  @Schema(description = "The date the rating was given. Absent when there is no rating.", example = "2026-11-03")
  val ratingDate: LocalDate? = null,

  @Field(type = FieldType.Date, format = [DateFormat.date])
  @Schema(description = "The review due date to show beside the rating, or absent where none should be shown (Standard, No rating and provisional ratings).", example = "2027-05-06")
  val reviewDueDate: LocalDate? = null,
)
