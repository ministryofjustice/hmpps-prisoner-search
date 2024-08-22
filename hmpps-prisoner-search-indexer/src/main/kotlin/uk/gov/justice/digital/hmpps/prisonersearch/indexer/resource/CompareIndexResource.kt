package uk.gov.justice.digital.hmpps.prisonersearch.indexer.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.CompareIndexService

@RestController
@Validated
@RequestMapping("/compare-index", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "OpenSearch index comparison")
class CompareIndexResource(private val compareIndexService: CompareIndexService) {
  @GetMapping("/size")
  @Operation(
    summary = "Simple OpenSearch index size comparison.",
    description = """Comparison of the number of prisoners in NOMIS and the number of prisoners in the index.
        Results sent to a custom event called COMPARE_INDEX_SIZE.
        It is an internal service which isn't exposed to the outside world and is called from a
        Kubernetes CronJob named `compare-index-size-cronjob`
      """,
  )
  fun compareIndexSizes() = compareIndexService.doIndexSizeCheck()

  @GetMapping("/ids")
  @PreAuthorize("hasRole('PRISONER_INDEX')")
  @ResponseStatus(HttpStatus.ACCEPTED)
  @Operation(
    summary = "Comparison of prisoners in NOMIS and OpenSearch by prisoner numbers.",
    description = """This endpoint will retrieve the list of all prisoner numbers in NOMIS and compare them against the list in the
      index.  It will then send a COMPARE_INDEX_IDS custom event to application insights with prisoner numbers of
      prisoners that are only in NOMIS and those that are only in OpenSearch.  It is asynchronous since the comparison
      can take a while.
      Requires ROLE_PRISONER_INDEX.
      """,
  )
  fun compareIndexByIds(): Unit = compareIndexService.doCompareByIds()

  @GetMapping("/red")
  @PreAuthorize("hasRole('PRISONER_INDEX')")
  @ResponseStatus(HttpStatus.ACCEPTED)
  @Operation(
    summary = "Comparison of prisoners in the existing and the new RED indices",
    description = """This endpoint will retrieve all prisoners in the current GREEN or BLUE index and compare with the RED index.
      It will then send a COMPARE_INDEX_IDS custom event to application insights with any discrepancies.
      It is asynchronous since the comparison can take a while.
      Requires ROLE_PRISONER_INDEX.
      """,
  )
  fun compareOldAndNewIndex(): Unit = compareIndexService.compareOldAndNewIndex()

  @GetMapping("/prisoner/{prisonerNumber}")
  @Operation(
    summary = "Compare a prisoner's index with Nomis",
    description = "Existing index is compared in detail with current Nomis data for a specific prisoner, requires ROLE_PRISONER_INDEX.",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an authorisation with role PRISONER_INDEX"),
      ApiResponse(responseCode = "404", description = "Not Found, the offender could not be found"),
      ApiResponse(responseCode = "409", description = "Conflict, no indexes could be updated"),
    ],
  )
  @PreAuthorize("hasRole('PRISONER_INDEX')")
  fun comparePrisoner(
    @Parameter(required = true, example = "A1234AA")
    @NotNull
    @Pattern(regexp = "[a-zA-Z][0-9]{4}[a-zA-Z]{2}")
    @PathVariable("prisonerNumber")
    prisonerNumber: String,
  ): String =
    // deliberately return a string here as need to call toString on org.apache.commons.lang3.builder.Diff
    // so that we get a proper representation of each difference
    compareIndexService.comparePrisoner(prisonerNumber).toString()
}
