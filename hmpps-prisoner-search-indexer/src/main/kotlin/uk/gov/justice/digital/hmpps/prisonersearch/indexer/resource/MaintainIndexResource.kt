package uk.gov.justice.digital.hmpps.prisonersearch.indexer.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.MaintainIndexService

@RestController
@Validated
@RequestMapping("/maintain-index", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "OpenSearch index maintenance")
class MaintainIndexResource(private val maintainIndexService: MaintainIndexService) {

  @PutMapping("/index-prisoner/{prisonerNumber}")
  @PreAuthorize("hasRole('PRISONER_INDEX')")
  @Operation(
    summary = "Index / refresh data for prisoner with specified prisoner number",
    description = """Updates the prisoner details for the specified prisoner.
      Returns the prisoner details added to the index. Requires PRISONER_INDEX role.""",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an authorisation with role PRISONER_INDEX"),
      ApiResponse(responseCode = "404", description = "Not Found, the offender could not be found"),
      ApiResponse(responseCode = "409", description = "Conflict, no indexes could be updated"),
    ],
  )
  fun indexPrisoner(
    @Parameter(required = true, example = "A1234AA")
    @NotNull
    @Pattern(regexp = "[a-zA-Z][0-9]{4}[a-zA-Z]{2}")
    @PathVariable("prisonerNumber")
    prisonerNumber: String,
  ) = maintainIndexService.indexPrisoner(prisonerNumber)
}
