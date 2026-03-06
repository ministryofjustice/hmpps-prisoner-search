package uk.gov.justice.digital.hmpps.prisonersearch.indexer.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
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
      prisoner data is then refreshed from Nomis. By default this will also cause domain events to be generated.
      <p>Note this is a heavyweight operation, rebuilding the whole index""",
  )
  @PreAuthorize("hasRole('PRISONER_INDEX')")
  @ResponseStatus(HttpStatus.ACCEPTED)
  fun startFullIndexRefresh(
    @Schema(description = "Control whether domain events are generated", example = "false", required = false)
    @RequestParam(name = "domain-events", required = false, defaultValue = "true")
    domainEvents: Boolean,
  ) = refreshIndexService.startFullIndexRefresh(domainEvents)

  @PutMapping("/active")
  @Operation(
    summary = "Start an active only refresh of the index.",
    description = """The active booking only prisoners are compared in detail with current Nomis data, requires ROLE_PRISONER_INDEX.
      Results are written as <code>customEvents</code>. When a prisoner's data matches no event is generated and no data is changed.
      If the prisoner data is different then DIFFERENCE_REPORTED or DIFFERENCE_MISSING event is generated and the
      prisoner data is then refreshed from Nomis. By default this will also cause domain events to be generated.
      """,
  )
  @PreAuthorize("hasRole('PRISONER_INDEX')")
  @ResponseStatus(HttpStatus.ACCEPTED)
  fun startActiveIndexRefresh(
    @Schema(description = "Control whether domain events are generated", example = "false", required = false)
    @RequestParam(name = "domain-events", required = false, defaultValue = "true")
    domainEvents: Boolean,
  ) = refreshIndexService.startActiveIndexRefresh(domainEvents)
}
