package uk.gov.justice.digital.hmpps.prisonersearch.search.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springdoc.core.annotations.ParameterObject
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.RestrictedPatientSearchCriteria
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.RestrictedPatientSearchService

@RestController
@Validated
@PreAuthorize("hasAnyRole('ROLE_GLOBAL_SEARCH', 'ROLE_PRISONER_SEARCH')")
@RequestMapping(
  "/restricted-patient-search",
  produces = [MediaType.APPLICATION_JSON_VALUE],
  consumes = [MediaType.APPLICATION_JSON_VALUE],
)
class RestrictedPatientSearchResource(private val restrictedPatientSearchService: RestrictedPatientSearchService) {

  @PostMapping("/match-restricted-patients")
  @Operation(summary = "Match prisoners by criteria", description = "Requires ROLE_GLOBAL_SEARCH or ROLE_PRISONER_SEARCH role")
  @Tag(name = "Specific use case")
  fun findByCriteria(
    @Parameter(required = true) @RequestBody searchCriteria: RestrictedPatientSearchCriteria,
    @RequestParam(value = "responseFields", required = false)
    @Parameter(
      description = "A list of fields to populate on the Prisoner record returned in the response. An empty list defaults to all fields.",
      example = "[prisonerNumber,firstName,aliases.firstName,currentIncentive.level.code]",
    )
    responseFields: List<String>? = null,
    @ParameterObject @PageableDefault
    pageable: Pageable,
  ) = restrictedPatientSearchService.findBySearchCriteria(searchCriteria, pageable, responseFields)
}
