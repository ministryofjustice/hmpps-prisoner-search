package uk.gov.justice.digital.hmpps.prisonersearch.search.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springdoc.core.annotations.ParameterObject
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.GlobalSearchCriteria
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.GlobalSearchService

@RestController
@Validated
class GlobalSearchResource(
  private val globalSearchService: GlobalSearchService,
) {

  @PostMapping(
    "/global-search",
    produces = [MediaType.APPLICATION_JSON_VALUE],
    consumes = [MediaType.APPLICATION_JSON_VALUE],
  )
  @PreAuthorize("hasAnyRole('ROLE_GLOBAL_SEARCH', 'ROLE_PRISONER_SEARCH')")
  @Operation(
    summary = "Match prisoners by criteria",
    description = "Requires ROLE_GLOBAL_SEARCH role or ROLE_PRISONER_SEARCH role",
  )
  @Tag(name = "Global search")
  @Tag(name = "Popular")
  fun globalFindByCriteria(
    @RequestBody globalSearchCriteria: GlobalSearchCriteria,
    @ParameterObject @PageableDefault
    pageable: Pageable,
  ) = globalSearchService.findByGlobalSearchCriteria(globalSearchCriteria, pageable)

  /*
  TODO move service call out of search
  @GetMapping("/prisoner/{id}")
  @PreAuthorize("hasAnyRole('ROLE_VIEW_PRISONER_DATA', 'ROLE_PRISONER_SEARCH')")
  @Operation(
    summary = "Get prisoner by prisoner number (AKA NOMS number)",
    description = "Requires ROLE_PRISONER_SEARCH or ROLE_VIEW_PRISONER_DATA role",
    security = [SecurityRequirement(name = "ROLE_VIEW_PRISONER_DATA"), SecurityRequirement(name = "ROLE_PRISONER_SEARCH")],
  )
  @Tag(name = "Popular")
  fun findByPrisonNumber(@PathVariable id: String) =
    prisonerIndexService.get(id).takeIf { it != null } ?: throw NotFoundException("$id not found")
  */
}
