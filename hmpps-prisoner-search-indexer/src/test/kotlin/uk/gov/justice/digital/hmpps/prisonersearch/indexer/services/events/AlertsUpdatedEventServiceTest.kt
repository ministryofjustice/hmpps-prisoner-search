package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.microsoft.applicationinsights.TelemetryClient
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.json.JsonTest
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.PrisonerAlert

private const val BOOKING_ID = "1203208"
private const val OFFENDER_NO = "A9460DY"

@JsonTest
internal class AlertsUpdatedEventServiceTest(@Autowired private val objectMapper: ObjectMapper) {
  private val domainEventsEmitter = mock<HmppsDomainEventEmitter>()
  private val telemetryClient = mock<TelemetryClient>()

  private val alertsUpdatedEventService = AlertsUpdatedEventService(domainEventsEmitter, telemetryClient)

  @Test
  internal fun `will not emit anything if changes are not related to alerts`() {
    val previousPrisonerSnapshot = prisoner()
    val prisoner = prisoner().apply {
      this.firstName = "BOBBY"
    }

    alertsUpdatedEventService.generateAnyEvents(previousPrisonerSnapshot, prisoner)

    verifyNoInteractions(domainEventsEmitter)
  }

  @Test
  internal fun `will emit event if alert added`() {
    val previousPrisonerSnapshot = prisoner()
    val prisoner = prisoner().apply {
      this.alerts = listOf(PrisonerAlert(alertType = "X", alertCode = "XA", active = true, expired = false))
    }

    alertsUpdatedEventService.generateAnyEvents(previousPrisonerSnapshot, prisoner)

    verify(domainEventsEmitter).emitPrisonerAlertsUpdatedEvent(
      offenderNo = OFFENDER_NO,
      bookingId = BOOKING_ID,
      alertsAdded = setOf("XA"),
      alertsRemoved = setOf(),
    )
  }

  @Test
  internal fun `will emit event if alert removed`() {
    val previousPrisonerSnapshot = prisoner().apply {
      this.alerts = listOf(PrisonerAlert(alertType = "X", alertCode = "XA", active = true, expired = false))
    }
    val prisoner = prisoner()

    alertsUpdatedEventService.generateAnyEvents(previousPrisonerSnapshot, prisoner)

    verify(domainEventsEmitter).emitPrisonerAlertsUpdatedEvent(
      offenderNo = OFFENDER_NO,
      bookingId = BOOKING_ID,
      alertsAdded = setOf(),
      alertsRemoved = setOf("XA"),
    )
  }

  @Test
  internal fun `will emit event if alerts both add and removed`() {
    val previousPrisonerSnapshot = prisoner().apply {
      this.alerts = listOf(
        PrisonerAlert(alertType = "X", alertCode = "XA", active = true, expired = false),
        PrisonerAlert(alertType = "X", alertCode = "XT", active = true, expired = false),
        PrisonerAlert(alertType = "X", alertCode = "AA", active = true, expired = false),
      )
    }
    val prisoner = prisoner().apply {
      this.alerts = listOf(
        PrisonerAlert(alertType = "X", alertCode = "XA", active = true, expired = false),
        PrisonerAlert(alertType = "X", alertCode = "XK", active = true, expired = false),
        PrisonerAlert(alertType = "X", alertCode = "BB", active = true, expired = false),
      )
    }

    alertsUpdatedEventService.generateAnyEvents(previousPrisonerSnapshot, prisoner)

    verify(domainEventsEmitter).emitPrisonerAlertsUpdatedEvent(
      offenderNo = OFFENDER_NO,
      bookingId = BOOKING_ID,
      alertsAdded = setOf("XK", "BB"),
      alertsRemoved = setOf("XT", "AA"),
    )
  }

  private fun prisoner(): Prisoner = objectMapper.readValue(
    AlertsUpdatedEventService::class.java.getResource("/receive-state-changes/first-new-booking.json")!!.readText(),
  )
}
