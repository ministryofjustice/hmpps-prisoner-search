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
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IndexStatus
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.SyncIndex.NONE
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.MaintainIndexService

@RestController
@Validated
@RequestMapping("/maintain-index", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "OpenSearch index maintenance")
class MaintainIndexResource(private val maintainIndexService: MaintainIndexService) {

  @PutMapping("/build")
  @PreAuthorize("hasRole('PRISONER_INDEX')")
  @Operation(
    summary = "Start building a new index",
    description = """The current index will be left untouched and continue to be maintained while the new index is built.
      The new index must not be currently building.  Requires PRISONER_INDEX role.  Returns the new status of the index.""",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an authorisation with role PRISONER_INDEX"),
      ApiResponse(responseCode = "409", description = "Conflict, the index was not in a state to start building"),
    ],
  )
  fun buildIndex(): IndexStatus = maintainIndexService.prepareIndexForRebuild(NONE)

  @PutMapping("/cancel")
  @PreAuthorize("hasRole('PRISONER_INDEX')")
  @Operation(
    summary = "Cancel building an index",
    description = """Cancels the building of the current index if it is currently building.
      Requires PRISONER_INDEX role.  Returns the new status of the index.""",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an authorisation with role PRISONER_INDEX"),
    ],
  )
  fun cancelIndex() = maintainIndexService.cancelIndexing()

  @PutMapping("/mark-complete")
  @PreAuthorize("hasRole('PRISONER_INDEX')")
  @Operation(
    summary = "Mark the current index build as complete",
    description = """Completes the index build if it is currently building and has reached the required threshold.
      Requires PRISONER_INDEX role.  Returns the new status of the index.""",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an authorisation with role PRISONER_INDEX"),
      ApiResponse(responseCode = "409", description = "Conflict, the index was not currently building"),
    ],
  )
  fun markComplete(@RequestParam(name = "ignoreThreshold", required = false) ignoreThreshold: Boolean = false) =
    maintainIndexService.markIndexingComplete(ignoreThreshold)

  @PutMapping("/switch")
  @PreAuthorize("hasRole('PRISONER_INDEX')")
  @Operation(
    summary = "Switch index without rebuilding",
    description = """Current index will be switched. Both indexed have to be complete, requires PRISONER_INDEX role.""",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an authorisation with role PRISONER_INDEX"),
      ApiResponse(
        responseCode = "409",
        description = "Conflict, the index was not able to be swapped as other index not complete",
      ),
    ],
  )
  fun switchIndex(@RequestParam(name = "force", required = false) force: Boolean = false) =
    maintainIndexService.switchIndex(force)

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

  @PutMapping("/check-complete")
  @Operation(
    summary = "Checks to see if the index has finished building",
    description = """This job checks to see if there are no more messages on the index queue and therefore indexing is
      complete.  If the index isn't currently building then no action will be taken.
      It also has a safety check to ensure that we have a minimum number of messages in the index.
      It is an internal service which isn't exposed to the outside world and is called from a Kubernetes CronJob named
      `check-indexing-complete`
      """,
  )
  fun checkIfComplete() = maintainIndexService.markIndexingComplete(ignoreThreshold = false)
}
