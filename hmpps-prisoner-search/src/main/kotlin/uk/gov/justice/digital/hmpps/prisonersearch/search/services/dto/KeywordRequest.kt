package uk.gov.justice.digital.hmpps.prisonersearch.search.services.dto

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.Gender
import java.time.LocalDate

data class KeywordRequest(
  @Schema(
    description = "Match where any of the keywords are present in any text field",
    example = "smith james john",
    required = false,
  )
  val orWords: String? = null,

  @Schema(
    description = "Match where all keywords are present in any text field",
    example = "smith james",
    required = false,
  )
  val andWords: String? = null,

  @Schema(
    description = "Filter results where any of these words are present in any text field",
    example = "jonas",
    required = false,
  )
  val notWords: String? = null,

  @Schema(
    description = "Match only prisoners where the full phrase is present in any text field",
    example = "John Smith",
    required = false,
  )
  val exactPhrase: String? = null,

  @Schema(
    description = "Fuzzy matching. Allow a one character difference in spelling in word lengths below five and two differences above.",
    example = "Smith will match Smyth",
    required = false,
  )
  val fuzzyMatch: Boolean? = false,

  @Schema(
    description = "List of prison codes to filter results, null means all",
    example = "[\"LEI\", \"MDI\"]",
    required = false,
  )
  val prisonIds: List<String>? = null,

  @Schema(
    description = "Pagination options. Will default to the first page if omitted.",
    required = false,
  )
  val pagination: PaginationRequest = PaginationRequest(0, 10),

  @Schema(
    description = "The type of search. When set to DEFAULT (which is the default when not provided) search order is by calculated relevance (AKA score). An ESTABLISHMENT type will order results by name and is designed for using this API for a single quick search field for prisoners within a specific prison",
    required = false,
  )
  val type: SearchType = SearchType.DEFAULT,

  @Schema(
    description = "Gender, F - Female, M - Male, NK - Not Known / Not Recorded or NS - Not Specified (Indeterminate)",
    example = "M",
  )
  val gender: Gender? = null,
  @Schema(description = "Location, Inside or Outside", example = "IN")
  val location: String? = null,
  @Schema(description = "Date of birth", example = "1970-02-28")
  val dateOfBirth: LocalDate? = null,
)

enum class SearchType {
  DEFAULT,
  ESTABLISHMENT,
}
