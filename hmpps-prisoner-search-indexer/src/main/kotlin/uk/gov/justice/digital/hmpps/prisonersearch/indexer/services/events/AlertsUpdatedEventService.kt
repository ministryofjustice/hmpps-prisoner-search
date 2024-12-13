package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.TelemetryEvents

@Service
class AlertsUpdatedEventService(
  private val domainEventEmitter: HmppsDomainEventEmitter,
  private val telemetryClient: TelemetryClient,
) {
  fun generateAnyEvents(
    previousPrisonerSnapshot: Prisoner?,
    prisoner: Prisoner,
    red: Boolean = false,
  ) {
    val previousAlerts = previousPrisonerSnapshot?.alerts?.map { it.alertCode }?.toSet() ?: emptySet()
    val alerts = prisoner.alerts?.map { it.alertCode }?.toSet() ?: emptySet()
    val alertsAdded = alerts - previousAlerts
    val alertsRemoved = previousAlerts - alerts

    if (alertsAdded.isNotEmpty() || alertsRemoved.isNotEmpty()) {
      if (red) {
        telemetryClient.trackEvent(
          TelemetryEvents.RED_SIMULATE_ALERT_EVENT.toString(),
          mapOf(
            "prisoner" to prisoner.prisonerNumber,
            "added" to alertsAdded.size.toString(),
            "removed" to alertsRemoved.size.toString(),
          ),
          null,
        )
      } else {
        domainEventEmitter.emitPrisonerAlertsUpdatedEvent(prisoner.prisonerNumber!!, prisoner.bookingId, alertsAdded, alertsRemoved)
      }
    }
  }
}
