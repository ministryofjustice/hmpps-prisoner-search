package uk.gov.justice.digital.hmpps.prisonersearch.search.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.GlobalSearchCriteria
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.GlobalSearchService
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.PrisonerSearchService
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.SearchCriteria
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.dto.PaginationRequest
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.exceptions.NotFoundException
import kotlin.collections.firstOrNull

@RestController
@Validated
class GlobalSearchResource(
  private val globalSearchService: GlobalSearchService,
  private val prisonerSearchService: PrisonerSearchService,
) {

  @PostMapping(
    "/global-search",
    produces = [MediaType.APPLICATION_JSON_VALUE],
    consumes = [MediaType.APPLICATION_JSON_VALUE],
  )
  @PreAuthorize("hasAnyRole('ROLE_GLOBAL_SEARCH', 'ROLE_PRISONER_SEARCH', 'PRISONER_SEARCH__PRISONER__RO')")
  @Operation(
    summary = "Match prisoners by criteria",
    description = """This endpoint sorts by OpenSearch score and then by prisonerNumber. The score is an indication of
      how close the query matches a prisoner record, thus closest matches to the query will be returned first.
      Unfortunately sorting by score is problematic as different shards might provide different scores, thus breaking
      the paged results. Also it gives inconsistent results the higher the page number. 
      
      It is thus recommended not to use paging and instead request a large page size, together with setting the
      responseFields to limit the returned response byte size (otherwise you risk hitting memory / webclient limits).

      Requires ROLE_GLOBAL_SEARCH role or ROLE_PRISONER_SEARCH role or PRISONER_SEARCH__PRISONER__RO""",
  )
  @Tag(name = "Global search")
  @Tag(name = "Popular")
  fun globalFindByCriteria(
    @RequestBody globalSearchCriteria: GlobalSearchCriteria,
    @RequestParam(value = "page", defaultValue = "0")
    @Parameter(description = "Zero-based page index (0..N). Will default to 0 if not supplied or invalid.", schema = Schema(defaultValue = "0", minimum = "0", type = "integer"))
    page: Int,
    @RequestParam(value = "size", defaultValue = "10")
    @Parameter(description = "The size of the page to be returned. Will default to 10 if not supplied or invalid.", schema = Schema(defaultValue = "10", minimum = "1", type = "integer"))
    size: Int,
    @RequestParam(value = "responseFields", required = false)
    @Parameter(
      description = "A list of fields to populate on the Prisoner record returned in the response. An empty list defaults to all fields.",
      example = "[prisonerNumber,firstName,aliases.firstName,currentIncentive.level.code]",
    )
    responseFields: List<String>? = null,
  ) = globalSearchService.findByGlobalSearchCriteria(globalSearchCriteria, PaginationRequest(page = page, size = size), responseFields)

  @GetMapping("/prisoner/{id}", produces = [MediaType.APPLICATION_JSON_VALUE])
  @PreAuthorize("hasAnyRole('ROLE_VIEW_PRISONER_DATA', 'ROLE_PRISONER_SEARCH', 'PRISONER_SEARCH__PRISONER__RO')")
  @Operation(
    summary = "Get prisoner by prisoner number (AKA NOMS number)",
    description = "Requires  ROLE_PRISONER_SEARCH or ROLE_VIEW_PRISONER_DATA role or PRISONER_SEARCH__PRISONER__RO",
    security = [SecurityRequirement(name = "view-prisoner-data-role"), SecurityRequirement(name = "prisoner-search-role")],
  )
  @Tag(name = "Popular")
  fun findByPrisonNumber(
    @PathVariable id: String,
    @RequestParam(value = "responseFields", required = false)
    @Parameter(
      description = "A list of fields to populate on the Prisoner record returned in the response. An empty list defaults to all fields.",
      example = "[prisonerNumber,firstName,aliases.firstName,currentIncentive.level.code]",
    )
    responseFields: List<String>? = null,
  ): Prisoner? = prisonerSearchService.findBySearchCriteria(SearchCriteria(id, null, null), responseFields).firstOrNull()
    ?: throw NotFoundException("$id not found")
}
