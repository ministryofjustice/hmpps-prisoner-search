package uk.gov.justice.digital.hmpps.prisonersearch.search.resource

import io.swagger.v3.oas.annotations.Hidden
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.AttributeSearchService
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.AttributeSearchRequest

@RestController
@Validated
@PreAuthorize("hasAnyRole('ROLE_GLOBAL_SEARCH', 'ROLE_PRISONER_SEARCH')")
@RequestMapping(
  "/attribute-search",
  produces = [MediaType.APPLICATION_JSON_VALUE],
  consumes = [MediaType.APPLICATION_JSON_VALUE],
)
class AttributeSearchResource(private val attributeSearchService: AttributeSearchService) {

  @PostMapping
  @Operation(
    summary = "WIP - DO NOT USE Search for prisoners by attributes",
    description = "Requires ROLE_GLOBAL_SEARCH or ROLE_PRISONER_SEARCH role",
  )
  @Hidden // TODO SDIT-1490 remove when the OpenAPI docs have been written
  fun attributeSearch(@Parameter(required = true) @RequestBody request: AttributeSearchRequest) =
    attributeSearchService.search(request)
}
