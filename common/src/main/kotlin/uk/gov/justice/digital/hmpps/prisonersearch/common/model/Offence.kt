package uk.gov.justice.digital.hmpps.prisonersearch.common.model

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.data.elasticsearch.annotations.DateFormat
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType
import java.time.LocalDate

data class Offence(
  @Schema(description = "Offence code", example = "OF61014")
  val code: String,

  @Schema(description = "Offence description", example = "Threats to kill")
  val description: String,

  @Field(type = FieldType.Date, format = [DateFormat.date])
  @Schema(description = "Offence date", example = "2023-04-24")
  val offenceDate: LocalDate? = null,

  @Schema(description = "Indicates offence is from the latest booking", example = "true")
  val latestBooking: Boolean,

  @Schema(description = "Indicates the offence is active", example = "true")
  val active: Boolean = true,

  @Schema(description = "Indicates the offence is convicted", example = "true")
  val convicted: Boolean = true,
)
