package uk.gov.justice.digital.hmpps.prisonersearch.common.model

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.data.elasticsearch.annotations.DateFormat
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType
import java.time.LocalDate

data class Offence(
  @Schema(description = "The statue code. Will never be null.", example = "TH68")
  val statuteCode: String? = null,

  @Schema(description = "The offence code. Will never be null.", example = "TH68010")
  val offenceCode: String? = null,

  @Schema(description = "The offence description. Will never be null.", example = "Theft from a shop")
  val offenceDescription: String? = null,

  @Schema(description = "The date of the offence", example = "2024-05-23")
  @Field(type = FieldType.Date, format = [DateFormat.date])
  val offenceDate: LocalDate? = null,

  @Schema(description = "Indicates this offence is for the latest NOMIS booking. Will never be null.")
  val latestBooking: Boolean? = null,

  @Schema(description = "Start date of sentence - null if there is no associated sentence", example = "2018-03-10")
  @Field(type = FieldType.Date, format = [DateFormat.date])
  val sentenceStartDate: LocalDate? = null,

  @Schema(description = "Primary sentence - true if it is not a consecutive sentence, false if it is a consecutive sentence, null if no sentence found for the charge.")
  val primarySentence: Boolean? = null,
)
