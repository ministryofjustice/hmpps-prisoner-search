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

  @Schema(description = "Start date of sentence - null if there is no associated sentence", example = "2018-03-10")
  @Field(type = FieldType.Date, format = [DateFormat.date])
  val sentenceStartDate: LocalDate? = null,

  @Schema(description = "Primary sentence - true if it is not a consecutive sentence, false if it is a consecutive sentence, null if no sentence found for the charge.")
  val primarySentence: Boolean? = null,
)
