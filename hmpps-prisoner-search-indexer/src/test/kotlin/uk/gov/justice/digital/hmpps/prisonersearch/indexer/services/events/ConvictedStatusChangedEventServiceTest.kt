package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.json.JsonTest
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner

private const val BOOKING_ID = "1203208"
private const val OFFENDER_NO = "A9460DY"

@JsonTest
internal class ConvictedStatusChangedEventServiceTest(@Autowired private val objectMapper: ObjectMapper) {
  private val domainEventsEmitter = mock<HmppsDomainEventEmitter>()

  private val convictedStatusEventService = ConvictedStatusEventService(domainEventsEmitter)

  @Test
  internal fun `will not emit anything if changes are not related to convictedStatus`() {
    val previousPrisonerSnapshot = prisoner()
    val prisoner = prisoner().apply {
      this.firstName = "BOBBY"
    }

    convictedStatusEventService.generateAnyEvents(previousPrisonerSnapshot, prisoner)

    verifyNoInteractions(domainEventsEmitter)
  }

  @Test
  internal fun `will emit event if convicted status added`() {
    val previousPrisonerSnapshot = prisoner().apply { convictedStatus = null }
    val prisoner = prisoner()

    convictedStatusEventService.generateAnyEvents(previousPrisonerSnapshot, prisoner)

    verify(domainEventsEmitter).emitConvictedStatusChangedEvent(
      offenderNo = OFFENDER_NO,
      bookingId = BOOKING_ID,
      convictedStatus = "Convicted",
    )
  }

  @Test
  internal fun `will emit event if convicted status removed`() {
    val previousPrisonerSnapshot = prisoner()
    val prisoner = prisoner().apply {
      this.convictedStatus = null
    }

    convictedStatusEventService.generateAnyEvents(previousPrisonerSnapshot, prisoner)

    verify(domainEventsEmitter).emitConvictedStatusChangedEvent(
      offenderNo = OFFENDER_NO,
      bookingId = BOOKING_ID,
      convictedStatus = null,
    )
  }

  @Test
  internal fun `will emit event if convictedStatus changed`() {
    val previousPrisonerSnapshot = prisoner()

    val prisoner = prisoner().apply {
      this.convictedStatus = "Remand"
    }

    convictedStatusEventService.generateAnyEvents(previousPrisonerSnapshot, prisoner)

    verify(domainEventsEmitter).emitConvictedStatusChangedEvent(
      offenderNo = OFFENDER_NO,
      bookingId = BOOKING_ID,
      convictedStatus = "Remand",
    )
  }

  private fun prisoner(): Prisoner = objectMapper.readValue(
    ConvictedStatusEventService::class.java.getResource("/receive-state-changes/first-new-booking.json")!!.readText(),
  )
}
