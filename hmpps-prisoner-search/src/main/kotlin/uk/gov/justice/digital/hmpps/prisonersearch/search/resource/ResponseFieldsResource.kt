package uk.gov.justice.digital.hmpps.prisonersearch.search.resource

import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Validated
@PreAuthorize("hasAnyRole('ROLE_GLOBAL_SEARCH', 'ROLE_PRISONER_SEARCH')")
@RequestMapping("/response-fields", produces = [MediaType.APPLICATION_JSON_VALUE])
class ResponseFieldsResource(private val responseFields: List<String>) {
  // TODO SDIT-2691 Add OpenAPI spec. details when this is ready for general consumption
  @GetMapping
  fun responseFields() = responseFields
}
