package uk.gov.justice.digital.hmpps.prisonersearch.common.model

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.data.elasticsearch.annotations.DateFormat
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType
import java.time.LocalDate

data class PrisonerAlias(
  @Schema(description = "Title", example = "Ms")
  var title: String? = null,

  @Schema(description = "First Name", example = "Robert")
  val firstName: String? = null,

  @Schema(description = "Middle names", example = "Trevor")
  val middleNames: String? = null,

  @Schema(description = "Last name", example = "Lorsen")
  val lastName: String? = null,

  @Field(type = FieldType.Date, format = [DateFormat.date])
  @Schema(description = "Date of birth", example = "1975-04-02")
  val dateOfBirth: LocalDate? = null,

  @Schema(description = "Gender", example = "Male")
  val gender: String? = null,

  @Schema(description = "Ethnicity", example = "White : Irish")
  val ethnicity: String? = null,

  @Schema(description = "Ethnicity code", example = "W1")
  val raceCode: String? = null,
)
