package uk.gov.justice.digital.hmpps.prisonersearch.search.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.PrisonerDetailService
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.dto.PrisonerDetailRequest
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse

@RestController
@Validated
@RequestMapping(value = ["/prisoner-detail"], produces = [MediaType.APPLICATION_JSON_VALUE])
@PreAuthorize("hasAnyRole('ROLE_GLOBAL_SEARCH', 'ROLE_PRISONER_SEARCH')")
class PrisonerDetailResource(private val prisonerDetailService: PrisonerDetailService) {
  // A hack to allow swagger to determine the response schema with a generic content
  abstract class PrisonerDetailResponse : Page<Prisoner>

  @Operation(
    summary = "Find prisoners by exact or wildcard terms for specified fields and return a paginated result set",
    description = """Search terms and identifiers can be provided in either or mixed case and are converted to the appropriate case.
      This endpoint will find both exact values (full term matched) or wildcards supporting the '*' and '?' symbols.
      The '*' symbol will match any number of characters e.g. firstName='J*' will match 'John', 'Jane', and 'James'.  
      The '?' symbol will match any letter substituted at that position. e.g. firstName='t?ny' will match 'Tony' and 'Tiny'
      Requires ROLE_GLOBAL_SEARCH or ROLE_PRISONER_SEARCH role.
      """,
    security = [SecurityRequirement(name = "global-search-role"), SecurityRequirement(name = "prisoner-search-role")],
    requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
      content = [
        Content(
          mediaType = "application/json",
          schema = Schema(implementation = PrisonerDetailRequest::class),
        ),
      ],
    ),
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Search successfully performed",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = PrisonerDetailResponse::class))],
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect information provided to perform prisoner match",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to search for prisoner data",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  @PostMapping
  @Tag(name = "Global search")
  fun prisonerDetailSearch(
    @Valid @RequestBody
    prisonerDetailRequest: PrisonerDetailRequest,
    @RequestParam(value = "responseFields", required = false)
    @Parameter(
      description = "A list of fields to populate on the Prisoner record returned in the response. An empty list defaults to all fields.",
      example = "[prisonerNumber,firstName,aliases.firstName,currentIncentive.level.code]",
    )
    responseFields: List<String>? = null,
  ): Page<Prisoner> = prisonerDetailService.findByPrisonerDetail(prisonerDetailRequest, responseFields)
}
