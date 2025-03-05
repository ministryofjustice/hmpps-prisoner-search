package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner

@Service
class AlertsUpdatedEventService(
  private val domainEventEmitter: HmppsDomainEventEmitter,
) {
  fun generateAnyEvents(
    previousPrisonerSnapshot: Prisoner?,
    prisoner: Prisoner,
    red: Boolean,
  ) {
    val previousAlerts = previousPrisonerSnapshot?.alerts?.map { it.alertCode }?.toSet() ?: emptySet()
    val alerts = prisoner.alerts?.map { it.alertCode }?.toSet() ?: emptySet()
    val alertsAdded = alerts - previousAlerts
    val alertsRemoved = previousAlerts - alerts

    if (alertsAdded.isNotEmpty() || alertsRemoved.isNotEmpty()) {
      domainEventEmitter.emitPrisonerAlertsUpdatedEvent(
        prisoner.prisonerNumber!!,
        prisoner.bookingId,
        alertsAdded,
        alertsRemoved,
        red,
      )
    }
  }
}
