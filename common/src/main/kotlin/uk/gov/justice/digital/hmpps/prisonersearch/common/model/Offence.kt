package uk.gov.justice.digital.hmpps.prisonersearch.common.model

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.data.elasticsearch.annotations.DateFormat
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType
import java.time.LocalDate

data class Offence(
  @Schema(description = "The statue code", example = "TH68")
  val statuteCode: String,

  @Schema(description = "The offence code", example = "TH68010")
  val offenceCode: String,

  @Schema(description = "The offence description", example = "Theft from a shop")
  val offenceDescription: String,

  @Schema(description = "The date of the offence", example = "2024-05-23")
  @Field(type = FieldType.Date, format = [DateFormat.date])
  val offenceDate: LocalDate? = null,

  @Schema(description = "Indicates this offence is for the latest NOMIS booking")
  val latestBooking: Boolean,
)
