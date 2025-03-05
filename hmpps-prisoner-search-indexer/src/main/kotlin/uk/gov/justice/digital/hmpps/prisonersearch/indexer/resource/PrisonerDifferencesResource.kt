package uk.gov.justice.digital.hmpps.prisonersearch.indexer.resource

import io.swagger.v3.oas.annotations.Hidden
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.repository.PrisonerDifferences
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.repository.PrisonerDifferencesLabel
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.PrisonerDifferencesService
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.time.Instant
import java.time.temporal.ChronoUnit

@RestController
@Validated
@RequestMapping(value = ["/prisoner-differences"], produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "OpenSearch differences report")
class PrisonerDifferencesResource(private val prisonerDifferencesService: PrisonerDifferencesService) {

  @Operation(
    summary = "Find all prisoner differences",
    description = """Find all prisoner differences since a given date time.  This defaults to within the last 24 hours.
      Requires PRISONER_INDEX role.
      """,
    security = [SecurityRequirement(name = "prisoner-index-role")],
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Search successfully performed",
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
  @GetMapping
  @PreAuthorize("hasRole('PRISONER_INDEX')")
  fun prisonerDifferences(
    @Parameter(description = "Select whether to get green/blue or red index differences. Possible values are GREEN_BLUE or RED (default)")
    @RequestParam(value = "label", required = false, defaultValue = "RED")
    label: PrisonerDifferencesLabel,
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @Parameter(description = "Report on differences that have been generated. Defaults to the last one day", example = "2023-01-02T02:23:45Z")
    from: Instant?,
    @Parameter(description = "Report on differences that have been generated. Defaults to the last one day", example = "2023-01-02T02:23:45Z")
    to: Instant?,
  ): List<PrisonerDifferences> = prisonerDifferencesService.retrieveDifferences(label, from ?: Instant.now().minus(1, ChronoUnit.DAYS), to ?: Instant.now())

  @Hidden
  @DeleteMapping("/delete")
  @Operation(
    summary = "Deletes differences data that is over a month old",
    description = "This is an internal service which isn't exposed to the outside world. It is called from a Kubernetes CronJob named `remove-old-differences`",
  )
  fun deleteOldData(): Int = prisonerDifferencesService.deleteOldData()
}
