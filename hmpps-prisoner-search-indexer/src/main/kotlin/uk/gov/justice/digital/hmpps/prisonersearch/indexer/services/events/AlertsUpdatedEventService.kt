package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.PrisonerAlert

@Service
class AlertsUpdatedEventService(
  private val domainEventEmitter: HmppsDomainEventEmitter,
) {
  fun generateAnyEvents(
    previousPrisonerSnapshot: Prisoner?,
    prisoner: Prisoner,
  ) {
    generateAnyEvents(previousPrisonerSnapshot?.alerts, prisoner.alerts, prisoner)
  }

  fun generateAnyEvents(
    previousAlerts: List<PrisonerAlert>?,
    alerts: List<PrisonerAlert>?,
    prisoner: Prisoner,
  ) {
    val previousAlertsSet = previousAlerts?.map { it.alertCode }?.toSet() ?: emptySet()
    val alertsSet = alerts?.map { it.alertCode }?.toSet() ?: emptySet()
    val alertsAdded = alertsSet - previousAlertsSet
    val alertsRemoved = previousAlertsSet - alertsSet
    if (alertsAdded.isNotEmpty() || alertsRemoved.isNotEmpty()) {
      domainEventEmitter.emitPrisonerAlertsUpdatedEvent(
        prisoner.prisonerNumber!!,
        prisoner.bookingId,
        alertsAdded.filterNotNull().toSet(),
        alertsRemoved.filterNotNull().toSet(),
      )
    }
  }
}
