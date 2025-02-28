package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner

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
      domainEventEmitter.emitConvictedStatusChangedEvent(
        offenderNo = prisoner.prisonerNumber!!,
        bookingId = prisoner.bookingId,
        convictedStatus = prisoner.convictedStatus,
        red,
      )
    }
  }
}

private fun Prisoner.convictionStatusChanged(previousPrisonerSnapshot: Prisoner?): Boolean = convictedStatus != previousPrisonerSnapshot?.convictedStatus
