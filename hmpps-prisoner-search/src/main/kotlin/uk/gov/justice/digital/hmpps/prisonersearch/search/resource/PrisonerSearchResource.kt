package uk.gov.justice.digital.hmpps.prisonersearch.search.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springdoc.core.annotations.ParameterObject
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.PrisonSearch
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.PrisonerListCriteria.BookingIds
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.PrisonerListCriteria.PrisonerNumbers
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.PrisonerSearchService
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.ReleaseDateSearch
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.SearchCriteria
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.dto.PossibleMatchCriteria

@RestController
@Validated
@PreAuthorize("hasAnyRole('ROLE_GLOBAL_SEARCH', 'ROLE_PRISONER_SEARCH', 'PRISONER_SEARCH__PRISONER__RO')")
@RequestMapping(
  "/prisoner-search",
  produces = [MediaType.APPLICATION_JSON_VALUE],
  consumes = [MediaType.APPLICATION_JSON_VALUE],
)
class PrisonerSearchResource(
  private val prisonerSearchService: PrisonerSearchService,
) {

  @Deprecated(message = "Use the /match-prisoners endpoint")
  @PostMapping("/match")
  @Operation(
    summary = "Match prisoners by criteria, to search across a list of specific prisons use /match-prisoners",
    description = "Requires ROLE_GLOBAL_SEARCH or ROLE_PRISONER_SEARCH role",
  )
  @Tag(name = "Deprecated")
  fun findByCriteria(@Parameter(required = true) @RequestBody prisonSearch: PrisonSearch) = prisonerSearchService.findBySearchCriteria(prisonSearch.toSearchCriteria())

  @PostMapping("/match-prisoners")
  @Operation(
    summary = "Match prisoners by criteria",
    description = """Search by prisoner identifier or name and returning results for the criteria matched first.
        Typically used when the matching data is of high quality where the first match is expected to be a near perfect match.
        Requires ROLE_GLOBAL_SEARCH or ROLE_PRISONER_SEARCH role.
        """,
  )
  @Tag(name = "Matching")
  @Tag(name = "Popular")
  fun findByCriteria(
    @Parameter(required = true) @RequestBody searchCriteria: SearchCriteria,
    @RequestParam(value = "responseFields", required = false)
    @Parameter(
      description = "A list of fields to populate on the Prisoner record returned in the response. An empty list defaults to all fields.",
      example = "[prisonerNumber,firstName,aliases.firstName,currentIncentive.level.code]",
    )
    responseFields: List<String>? = null,
  ) = prisonerSearchService.findBySearchCriteria(searchCriteria, responseFields)

  @PostMapping("/possible-matches")
  @Operation(
    summary = "Search for possible matches by criteria",
    description = """Search by prison number, PNC number, and/or name and date of birth, returning collated results by order of search.
       This will also search aliases for possible matches. 
       Use when there is manual input, e.g. a user can select the correct match from search results.
       Requires ROLE_GLOBAL_SEARCH or ROLE_PRISONER_SEARCH role.
       """,
  )
  @Tag(name = "Matching")
  fun findPossibleMatchesBySearchCriteria(
    @Parameter(required = true) @RequestBody searchCriteria: PossibleMatchCriteria,
    @RequestParam(value = "responseFields", required = false)
    @Parameter(
      description = "A list of fields to populate on the Prisoner record returned in the response. An empty list defaults to all fields.",
      example = "[prisonerNumber,firstName,aliases.firstName,currentIncentive.level.code]",
    )
    responseFields: List<String>? = null,
  ) = prisonerSearchService.findPossibleMatchesBySearchCriteria(searchCriteria, responseFields)

  @PostMapping("/prisoner-numbers")
  @Operation(
    summary = "Match prisoners by a list of prisoner numbers",
    description = "Requires ROLE_GLOBAL_SEARCH or ROLE_PRISONER_SEARCH role or PRISONER_SEARCH__PRISONER__RO",
  )
  @Tag(name = "Batch")
  @Tag(name = "Popular")
  fun findByNumbers(
    @Parameter(required = true) @Valid @RequestBody criteria: PrisonerNumbers,
    @RequestParam(value = "responseFields", required = false)
    @Parameter(
      description = "A list of fields to populate on the Prisoner record returned in the response. An empty list defaults to all fields.",
      example = "[prisonerNumber,firstName,aliases.firstName,currentIncentive.level.code]",
    )
    responseFields: List<String>? = null,
  ): List<Prisoner> = prisonerSearchService.findBy(criteria, responseFields)

  @PostMapping("/booking-ids")
  @Operation(
    summary = "Match prisoners by a list of booking ids",
    description = "Requires ROLE_GLOBAL_SEARCH or ROLE_PRISONER_SEARCH role",
  )
  @Tag(name = "Batch")
  fun findByIds(
    @Parameter(required = true) @Valid @RequestBody criteria: BookingIds,
    @RequestParam(value = "responseFields", required = false)
    @Parameter(
      description = "A list of fields to populate on the Prisoner record returned in the response. An empty list defaults to all fields.",
      example = "[prisonerNumber,firstName,aliases.firstName,currentIncentive.level.code]",
    )
    responseFields: List<String>? = null,
  ): List<Prisoner> = prisonerSearchService.findBy(criteria, responseFields)

  @PostMapping("/release-date-by-prison")
  @Operation(
    summary = "Match prisoners who have a release date within a range, and optionally by prison",
    description = "Requires ROLE_GLOBAL_SEARCH or ROLE_PRISONER_SEARCH role",
  )
  @Tag(name = "Batch")
  @Tag(name = "Specific use case")
  fun findByReleaseDateAndPrison(
    @Parameter(required = true) @Valid @RequestBody criteria: ReleaseDateSearch,
    @RequestParam(value = "responseFields", required = false)
    @Parameter(
      description = "A list of fields to populate on the Prisoner record returned in the response. An empty list defaults to all fields.",
      example = "[prisonerNumber,firstName,aliases.firstName,currentIncentive.level.code]",
    )
    responseFields: List<String>? = null,
    @ParameterObject @PageableDefault
    pageable: Pageable,
  ) = prisonerSearchService.findByReleaseDate(criteria, pageable, responseFields)

  @GetMapping("/prison/{prisonId}")
  @Operation(
    summary = "Get all prisoners in a prison, including restricted patients supported by a POM",
    description = "Requires ROLE_GLOBAL_SEARCH or ROLE_PRISONER_SEARCH role",
  )
  @Tag(name = "Batch")
  @Tag(name = "Popular")
  fun findByPrison(
    @Valid @PathVariable
    prisonId: String,
    @RequestParam(
      "include-restricted-patients",
      required = false,
      defaultValue = "false",
    ) includeRestrictedPatients: Boolean,
    @RequestParam(value = "responseFields", required = false)
    @Parameter(
      description = "A list of fields to populate on the Prisoner record returned in the response. An empty list defaults to all fields.",
      example = "[prisonerNumber,firstName,aliases.firstName,currentIncentive.level.code]",
    )
    responseFields: List<String>? = null,
    @ParameterObject @PageableDefault
    pageable: Pageable,
  ): Page<Prisoner> = prisonerSearchService.findByPrison(prisonId.uppercase(), pageable, includeRestrictedPatients, responseFields)
}
