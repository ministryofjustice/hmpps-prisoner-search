package uk.gov.justice.digital.hmpps.prisonersearch.common.model

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.data.elasticsearch.annotations.DateFormat
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType
import java.time.LocalDate

data class PrisonerAlias(
  @Schema(description = "Title. Will never be null.", example = "Ms")
  var title: String? = null,

  @Schema(description = "First Name. Will never be null.", example = "Robert")
  val firstName: String? = null,

  @Schema(description = "Middle names. Will never be null.", example = "Trevor")
  val middleNames: String? = null,

  @Schema(description = "Last name. Will never be null.", example = "Lorsen")
  val lastName: String? = null,

  @Field(type = FieldType.Date, format = [DateFormat.date])
  @Schema(description = "Date of birth. Will never be null.", example = "1975-04-02")
  val dateOfBirth: LocalDate? = null,

  @Schema(description = "Gender. Will never be null.", example = "Male")
  val gender: String? = null,

  @Schema(description = "Ethnicity. Will never be null.", example = "White : Irish")
  val ethnicity: String? = null,

  @Schema(description = "Ethnicity code. Will never be null.", example = "W1")
  val raceCode: String? = null,
)
