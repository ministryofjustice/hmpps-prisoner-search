package uk.gov.justice.digital.hmpps.prisonersearch.indexer.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.HmppsDomainEventEmitter
import java.time.LocalDateTime
import java.time.ZoneId

@RestController
@Validated
@Tag(name = "Domain events recovery")
class DomainEventsResource(private val domainEventEmitter: HmppsDomainEventEmitter) {
  @PutMapping("/events/prisoner/received/{prisonerNumber}")
  @ResponseStatus(HttpStatus.ACCEPTED)
  @Operation(
    summary = "Fires a domain event 'prisoner-offender-search.prisoner.received'. This is to be used in a catastrophic failure scenario when the original event was not raised",
    description = "Requires EVENTS_ADMIN role",
  )
  @PreAuthorize("hasRole('ROLE_EVENTS_ADMIN')")
  fun raisePrisonerReceivedEvent(
    @Parameter(
      required = true,
      example = "A1234AA",
    )
    @NotNull
    @Pattern(regexp = "[a-zA-Z][0-9]{4}[a-zA-Z]{2}")
    @PathVariable("prisonerNumber")
    prisonerNumber: String,
    @RequestBody
    @Valid
    details: PrisonerReceivedEventDetails,
  ) {
    val occurredAtTime = details.occurredAt.atZone(ZoneId.of("Europe/London")).toInstant()
    domainEventEmitter.emitPrisonerReceiveEvent(
      prisonerNumber,
      details.reason,
      details.prisonId,
      occurredAt = occurredAtTime,
    )
  }
}

data class PrisonerReceivedEventDetails(
  @Schema(description = "reason for receive event", required = true, example = "TRANSFERRED")
  val reason: HmppsDomainEventEmitter.PrisonerReceiveReason,
  @Schema(description = "prison agency id of new prison", required = true, example = "WWI")
  val prisonId: String,
  @Schema(description = "local date time movement happened", required = true, example = "2023-02-28T12:34:56")
  val occurredAt: LocalDateTime,
)
