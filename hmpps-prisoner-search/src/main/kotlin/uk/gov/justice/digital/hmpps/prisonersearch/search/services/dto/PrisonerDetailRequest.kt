package uk.gov.justice.digital.hmpps.prisonersearch.search.services.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

data class PrisonerDetailRequest(
  @Schema(
    description = "Prisoner first name",
    example = "john",
    required = false,
  )
  val firstName: String? = null,

  @Schema(
    description = "Prisoner last name",
    example = "smith",
    required = false,
  )
  val lastName: String? = null,

  @Schema(
    description = "Prisoner number (aka. offenderId, nomisId)",
    example = "A1234AA",
    required = false,
  )
  val nomsNumber: String? = null,

  @Schema(
    description = "Police National Computer (PNC) number",
    example = "2018/0123456X",
    required = false,
  )
  val pncNumber: String? = null,

  @Schema(
    description = "Criminal Records Office (CRO) number",
    example = "SF80/655108T",
    required = false,
  ) val croNumber: String? = null,

  @Schema(
    description = "Fuzzy matching. Allow a one character difference in spelling in word lengths below five and two differences above.",
    example = "Smith will match Smyth",
    required = false,
  )
  val fuzzyMatch: Boolean? = false,

  @Schema(
    description = "List of prison codes to filter results by",
    example = "['LEI', 'MDI']",
    required = true,
  )
  val prisonIds: List<String>? = emptyList(),

  @Schema(description = "Date of birth", example = "1970-02-28", required = false)
  val dateOfBirth: LocalDate? = null,

  @Schema(description = "Include aliases in search", example = "true", required = false, defaultValue = "true")
  val includeAliases: Boolean = true,

  @Schema(
    description = "Pagination options. Will default to the first page if omitted.",
    required = false,
  )
  val pagination: PaginationRequest = PaginationRequest(0, 10),
)
