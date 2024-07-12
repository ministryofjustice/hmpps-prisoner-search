package uk.gov.justice.digital.hmpps.prisonersearch.search.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springdoc.core.annotations.ParameterObject
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.GlobalSearchCriteria
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.GlobalSearchService
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.PrisonerSearchService
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.SearchCriteria
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.exceptions.NotFoundException

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
  @PreAuthorize("hasAnyRole('ROLE_GLOBAL_SEARCH', 'ROLE_PRISONER_SEARCH', 'PRISONER_SEARCH__CORE_PERSON__RO')")
  @Operation(
    summary = "Match prisoners by criteria",
    description = "Requires ROLE_GLOBAL_SEARCH role or ROLE_PRISONER_SEARCH role or PRISONER_SEARCH__CORE_PERSON__RO",
  )
  @Tag(name = "Global search")
  @Tag(name = "Popular")
  fun globalFindByCriteria(
    @RequestBody globalSearchCriteria: GlobalSearchCriteria,
    @ParameterObject @PageableDefault
    pageable: Pageable,
  ) = globalSearchService.findByGlobalSearchCriteria(globalSearchCriteria, pageable)

  @GetMapping("/prisoner/{id}")
  @PreAuthorize("hasAnyRole('ROLE_VIEW_PRISONER_DATA', 'ROLE_PRISONER_SEARCH', 'PRISONER_SEARCH__CORE_PERSON__RO')")
  @Operation(
    summary = "Get prisoner by prisoner number (AKA NOMS number)",
    description = "Requires  ROLE_PRISONER_SEARCH or ROLE_VIEW_PRISONER_DATA role or PRISONER_SEARCH__CORE_PERSON__RO",
    security = [SecurityRequirement(name = "view-prisoner-data-role"), SecurityRequirement(name = "prisoner-search-role")],
  )
  @Tag(name = "Popular")
  fun findByPrisonNumber(@PathVariable id: String): Prisoner? =
    prisonerSearchService.findBySearchCriteria(SearchCriteria(id, null, null)).firstOrNull()
      ?: throw NotFoundException("$id not found")
}
