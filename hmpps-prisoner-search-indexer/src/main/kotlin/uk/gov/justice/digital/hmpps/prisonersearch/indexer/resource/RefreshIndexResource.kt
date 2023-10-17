package uk.gov.justice.digital.hmpps.prisonersearch.indexer.resource

import io.swagger.v3.oas.annotations.Hidden
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.RefreshIndexService

@RestController
@Validated
@RequestMapping("/refresh-index", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "OpenSearch index refresh")
class RefreshIndexResource(private val refreshIndexService: RefreshIndexService) {
  @PutMapping
  @Operation(
    summary = "Start a full refresh of the index.",
    description = """The whole existing index is compared in detail with current Nomis data, requires ROLE_PRISONER_INDEX.
      Results are written as <code>customEvents</code>. When a prisoner's data matches no event is generated and no data is changed.
      If the prisoner data is different then DIFFERENCE_REPORTED or DIFFERENCE_MISSING event is generated and the
      prisoner data is then refreshed from Nomis.  This will also cause domain events to be generated.
      <p>Note this is a heavyweight operation, like a full index rebuild""",
  )
  @PreAuthorize("hasRole('PRISONER_INDEX')")
  @ResponseStatus(HttpStatus.ACCEPTED)
  fun startIndexRefresh() = refreshIndexService.startIndexRefresh()

  @Hidden
  @PutMapping("/automated")
  @Operation(
    summary = "Automated full refresh of the index.",
    description = """Same as /refresh-index, but this is an internal only endpoint called from the
      full-index-comparison cronjob.""",
  )
  @ResponseStatus(HttpStatus.ACCEPTED)
  fun automatedIndexRefresh() = refreshIndexService.startIndexRefresh()
}
