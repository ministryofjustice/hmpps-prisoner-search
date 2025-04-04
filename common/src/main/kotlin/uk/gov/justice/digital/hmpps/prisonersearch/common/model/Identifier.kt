package uk.gov.justice.digital.hmpps.prisonersearch.common.model

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.data.elasticsearch.annotations.DateFormat
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType
import java.time.LocalDate
import java.time.LocalDateTime

data class Identifier(
  @Schema(description = "The type of identifier", example = "PNC, CRO, DL, NINO")
  val type: String? = null,

  @Schema(description = "The identifier value", example = "12/394773H")
  val value: String? = null,

  @Schema(description = "The date the identifier was issued according to NOMIS", example = "2020-07-17")
  @Field(type = FieldType.Date, format = [DateFormat.date])
  val issuedDate: LocalDate? = null,

  @Schema(description = "Free text entered into NOMIS when the identifier was recorded.")
  val issuedAuthorityText: String? = null,

  @Schema(description = "The date/time the identifier was created in the system", example = "2020-07-17T12:34:56.833Z")
  @Field(type = FieldType.Date, format = [DateFormat.date_hour_minute_second])
  val createdDateTime: LocalDateTime? = null,
)
