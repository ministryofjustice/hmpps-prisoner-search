package uk.gov.justice.digital.hmpps.prisonersearch.search.resource

import io.swagger.v3.oas.annotations.Operation
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
  @GetMapping
  @Operation(
    summary = "Get all available response fields",
    description = "This exists for developers to find all available values for responseFields parameters. Requires ROLE_GLOBAL_SEARCH or ROLE_PRISONER_SEARCH role",
  )
  fun responseFields() = responseFields
}
