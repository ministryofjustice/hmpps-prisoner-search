package uk.gov.justice.digital.hmpps.prisonersearch.common.model

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.data.elasticsearch.annotations.DateFormat
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType
import java.time.LocalDate

data class Address(
  @Schema(description = "The full address on a single line", example = "1 Main Street, Crookes, Sheffield, South Yorkshire, S10 1BP, England")
  val fullAddress: String,

  @Schema(description = "The postal code", example = "S10 1BP")
  val postalCode: String?,

  @Schema(description = "The date the address became active according to NOMIS", example = "2020-07-17")
  @Field(type = FieldType.Date, format = [DateFormat.date])
  val startDate: LocalDate?,

  @Schema(description = "Whether the address is currently marked as the primary address", example = "true")
  val primaryAddress: Boolean,

  @Schema(description = "Phone numbers linked to the address. Note the phone number contains only numbers, no whitespace. Therefore searching on 'addresses.phoneNumbers.number' should not pass any non-numeric characters.")
  val phoneNumbers: List<PhoneNumber>? = null,
)
