package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner

@Service
class AlertsUpdatedEventService(
  private val domainEventEmitter: HmppsDomainEventEmitter,
) {
  private companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

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
        log.info("Simulated alerts event from RED index: added {}, removed {}", alertsAdded.size, alertsRemoved.size)
      } else {
        domainEventEmitter.emitPrisonerAlertsUpdatedEvent(prisoner.prisonerNumber!!, prisoner.bookingId, alertsAdded, alertsRemoved)
      }
    }
  }
}
