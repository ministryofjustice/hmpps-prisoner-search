package uk.gov.justice.digital.hmpps.prisonersearch.search.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonersearch.search.resource.advice.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.ReferenceDataAttribute
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.ReferenceDataResponse
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.ReferenceDataService

@RestController
@Validated
@RequestMapping(value = ["/reference-data"], produces = [MediaType.APPLICATION_JSON_VALUE])
@PreAuthorize("hasAnyRole('ROLE_GLOBAL_SEARCH', 'ROLE_PRISONER_SEARCH')")
@Tag(name = "Experimental")
class ReferenceDataResource(private val referenceDataService: ReferenceDataService) {
  @Operation(
    summary = "*** BETA *** Reference data search",
    description = """BETA endpoint - reference data returned reflects the data assigned to prisoners
      rather than all the possible values.
      Only to be used for searching existing data purposes.
      Requires ROLE_GLOBAL_SEARCH or ROLE_PRISONER_SEARCH role.
      """,
    security = [SecurityRequirement(name = "global-search-role"), SecurityRequirement(name = "prisoner-search-role")],
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Reference data search successfully performed",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ReferenceDataResponse::class))],
      ),
      ApiResponse(
        responseCode = "400",
        description = "Reference data search for attribute that isn't mapped",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to retrieve reference data",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  @GetMapping("/{attribute}")
  @Tag(name = "Reference data")
  fun referenceData(
    @Valid @PathVariable
    attribute: ReferenceDataAttribute,
  ): ReferenceDataResponse = referenceDataService.findReferenceData(attribute)
}
