package uk.gov.justice.digital.hmpps.prisonersearchindexer.resource

import arrow.core.getOrElse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus.CONFLICT
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import uk.gov.justice.digital.hmpps.prisonersearchindexer.model.IndexStatus
import uk.gov.justice.digital.hmpps.prisonersearchindexer.services.CancelBuildError
import uk.gov.justice.digital.hmpps.prisonersearchindexer.services.MaintainIndexService
import uk.gov.justice.digital.hmpps.prisonersearchindexer.services.MarkCompleteError
import uk.gov.justice.digital.hmpps.prisonersearchindexer.services.PrepareRebuildError
import uk.gov.justice.digital.hmpps.prisonersearchindexer.services.SwitchIndexError
import uk.gov.justice.digital.hmpps.prisonersearchindexer.services.UpdatePrisonerError

@RestController
@Validated
@RequestMapping("/maintain-index", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "OpenSearch index maintenance")
class MaintainIndexResource(private val maintainIndexService: MaintainIndexService) {

  @PutMapping("/build")
  @PreAuthorize("hasRole('PRISONER_INDEX')")
  @Operation(
    summary = "Start building a new index",
    description = "The current index will be left untouched and continue to be maintained while the new index is built.  The new index must not be currently building.  Requires PRISONER_INDEX role.  Returns the new status of the index.",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an authorisation with role PRISONER_INDEX"),
      ApiResponse(responseCode = "409", description = "Conflict, the index was not in a state to start building"),
    ],
  )
  fun buildIndex(): IndexStatus =
    maintainIndexService.prepareIndexForRebuild()
      .getOrElse { error ->
        log.error("Request to /maintain-index/build failed due to error {}", error)
        when (PrepareRebuildError.fromErrorClass(error)) {
          PrepareRebuildError.BUILD_IN_PROGRESS -> throw ResponseStatusException(CONFLICT, error.message())
          PrepareRebuildError.ACTIVE_MESSAGES_EXIST -> throw ResponseStatusException(CONFLICT, error.message())
        }
      }

  @PutMapping("/cancel")
  @PreAuthorize("hasRole('PRISONER_INDEX')")
  @Operation(
    summary = "Cancel building an index",
    description = "Cancels the building of the current index if it is currently building.  Requires PRISONER_INDEX role.  Returns the new status of the index.",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an authorisation with role PRISONER_INDEX"),
    ],
  )
  fun cancelIndex() = maintainIndexService.cancelIndexing()
    .getOrElse { error ->
      log.error("Request to /maintain-index/cancel failed due to error {}", error)
      when (CancelBuildError.fromErrorClass(error)) {
        CancelBuildError.BUILD_NOT_IN_PROGRESS -> throw ResponseStatusException(CONFLICT, error.message())
      }
    }

  @PutMapping("/mark-complete")
  @PreAuthorize("hasRole('PRISONER_INDEX')")
  @Operation(
    summary = "Mark the current index build as complete",
    description = "Completes the index build if it is currently building.  Requires PRISONER_INDEX role.  Returns the new status of the index.",
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
      .getOrElse { error ->
        log.error("Request to /maintain-index/mark-complete failed due to error {}", error)
        when (MarkCompleteError.fromErrorClass(error)) {
          MarkCompleteError.BUILD_NOT_IN_PROGRESS -> throw ResponseStatusException(CONFLICT, error.message())
          MarkCompleteError.ACTIVE_MESSAGES_EXIST -> throw ResponseStatusException(CONFLICT, error.message())
          MarkCompleteError.THRESHOLD_NOT_REACHED -> throw ResponseStatusException(CONFLICT, error.message())
        }
      }

  @PutMapping("/switch")
  @PreAuthorize("hasRole('PRISONER_INDEX')")
  @Operation(
    summary = "Switch index without rebuilding",
    description = "current index will be switched both indexed have to be complete, requires PRISONER_INDEX role",
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
      .getOrElse { error ->
        log.error("Request to /maintain-index/switch failed due to error {}", error)
        when (SwitchIndexError.fromErrorClass(error)) {
          SwitchIndexError.BUILD_IN_PROGRESS -> throw ResponseStatusException(CONFLICT, error.message())
          SwitchIndexError.BUILD_CANCELLED -> throw ResponseStatusException(CONFLICT, error.message())
          SwitchIndexError.BUILD_ABSENT -> throw ResponseStatusException(CONFLICT, error.message())
        }
      }

  @PutMapping("/index-prisoner/{prisonerNumber}")
  @PreAuthorize("hasRole('PRISONER_INDEX')")
  @Operation(
    summary = "Index/Refresh Data for Prisoner with specified prisoner number",
    description = "Requires PRISONER_INDEX role.  Returns the prisoner details added to the index.",
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
  ) = maintainIndexService.updatePrisoner(prisonerNumber)
    .getOrElse { error ->
      log.error("Request to /maintain-index/index-prisoner/$prisonerNumber failed due to error {}", error)
      when (UpdatePrisonerError.fromErrorClass(error)) {
        UpdatePrisonerError.NO_ACTIVE_INDEXES -> throw ResponseStatusException(CONFLICT, error.message())
        UpdatePrisonerError.PRISONER_NOT_FOUND -> throw ResponseStatusException(NOT_FOUND, error.message())
      }
    }

  @PutMapping("/check-complete")
  @Operation(
    summary = "Triggers maintenance of the index queue",
    description = """
      This job checks to see if there are no more messages on the index queue and therefore indexing is
      complete.  It also has a safety check to ensure that we have a minimum number of messages in the index.
      It is an internal service which isn't exposed to the outside world and is called from a Kubernetes CronJob named
      `index-housekeeping-cronjob`
      """,
  )
  fun checkIfComplete() {
    maintainIndexService.markIndexingComplete(ignoreThreshold = false)
      .getOrElse { error ->
        if (MarkCompleteError.fromErrorClass(error) == MarkCompleteError.THRESHOLD_NOT_REACHED) {
          log.warn(
            "Not marking index build complete but only because the minimum index size threshold of {} has not been reached",
            error,
          )
        }
      }
  }

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}
