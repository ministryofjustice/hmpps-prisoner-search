package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner

@Service
class ConvictedStatusEventService(
  private val domainEventEmitter: HmppsDomainEventEmitter,
) {
  fun generateAnyEvents(
    previousPrisonerSnapshot: Prisoner?,
    prisoner: Prisoner,
    red: Boolean,
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
