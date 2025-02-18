package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.TelemetryEvents
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.trackPrisonerEvent

@Service
class ConvictedStatusEventService(
  private val domainEventEmitter: HmppsDomainEventEmitter,
  private val telemetryClient: TelemetryClient,
) {
  fun generateAnyEvents(
    previousPrisonerSnapshot: Prisoner?,
    prisoner: Prisoner,
    red: Boolean = false,
  ) {
    if (prisoner.convictionStatusChanged(previousPrisonerSnapshot)) {
      if (red) {
        telemetryClient.trackPrisonerEvent(
          TelemetryEvents.RED_SIMULATE_CONVICTED_STATUS_CHANGED_EVENT,
          prisoner.prisonerNumber!!,
        )
      } else {
        domainEventEmitter.emitConvictedStatusChangedEvent(
          offenderNo = prisoner.prisonerNumber!!,
          bookingId = prisoner.bookingId,
          convictedStatus = prisoner.convictedStatus,
        )
      }
    }
  }
}

private fun Prisoner.convictionStatusChanged(previousPrisonerSnapshot: Prisoner?): Boolean = convictedStatus != previousPrisonerSnapshot?.convictedStatus
