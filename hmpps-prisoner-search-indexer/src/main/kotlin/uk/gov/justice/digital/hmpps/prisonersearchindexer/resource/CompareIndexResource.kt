package uk.gov.justice.digital.hmpps.prisonersearchindexer.resource

import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonersearchindexer.services.CompareIndexService

@RestController
@Validated
@RequestMapping("/compare-index", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "OpenSearch index comparison")
class CompareIndexResource(private val compareIndexService: CompareIndexService) {
  @GetMapping("/size")
  @Tag(
    name = "Simple OpenSearch index size comparison.",
    description = """
        Comparison of the number of prisoners in NOMIS and the number of prisoners in the index. 
        Results sent to a custom event called COMPARE_INDEX_SIZE.
        It is an internal service which isn't exposed to the outside world and is called from a 
        Kubernetes CronJob named `synthethic-monitor-cronjob`
      """,
  )
  suspend fun compareIndexSizes(): Unit = compareIndexService.doIndexSizeCheck()

  @GetMapping("/ids")
  @PreAuthorize("hasRole('PRISONER_INDEX')")
  @ResponseStatus(HttpStatus.ACCEPTED)
  @Tag(
    name = "Comparison of prisoners in NOMIS and OpenSearch by prisoner numbers.",
    description = """
      This endpoint will retrieve the list of all prisoner numbers in NOMIS and compare them against the list in the
      index.  It will then send a COMPARE_INDEX_IDS custom event to application insights with prisoner numbers of
      prisoners that are only in NOMIS and those that are only in OpenSearch.  It is asynchronous since the comparison
      can take a while.
      Requires ROLE_PRISONER_INDEX.
      """,
  )
  fun compareIndexByIds(): Unit = compareIndexService.doCompareByIds()

// TODO (PGP): Add back in the index and reconcile index functions
//
//  @GetMapping("/full")
//  @Operation(
//    summary = "Start a full index comparison",
//    description = """The whole existing index is compared in detail with current Nomis data, requires ROLE_PRISONER_INDEX.
//      Results are written as customEvents. Nothing is written where a prisoner's data matches.
//      Note this is a heavyweight operation, like a full index rebuild""",
//  )
//  @PreAuthorize("hasRole('PRISONER_INDEX')")
//  @ResponseStatus(HttpStatus.ACCEPTED)
//  fun startIndexReconciliation() = indexService.startIndexReconciliation()
//
//  @GetMapping("/reconcile-prisoner/{prisonerNumber}")
//  @Operation(
//    summary = "Compare a prisoner's index with Nomis",
//    description = "Existing index is compared in detail with current Nomis data for a specific prisoner, requires ROLE_PRISONER_INDEX.",
//  )
//  @PreAuthorize("hasRole('PRISONER_INDEX')")
//  fun reconcilePrisoner(
//    @Pattern(regexp = "[a-zA-Z][0-9]{4}[a-zA-Z]{2}")
//    @PathVariable("prisonerNumber")
//    prisonerNumber: String,
//  ): String = indexService.comparePrisonerDetail(prisonerNumber).toString()
}
