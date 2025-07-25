package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.json.JsonTest
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.model.nomis.OffenderBooking
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.model.nomis.OffenderIdentifier
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.HmppsDomainEventEmitter.PrisonerReceiveReason
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.HmppsDomainEventEmitter.PrisonerReceiveReason.NEW_ADMISSION
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.HmppsDomainEventEmitter.PrisonerReceiveReason.POST_MERGE_ADMISSION
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.HmppsDomainEventEmitter.PrisonerReceiveReason.READMISSION
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.HmppsDomainEventEmitter.PrisonerReceiveReason.READMISSION_SWITCH_BOOKING
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.HmppsDomainEventEmitter.PrisonerReceiveReason.RETURN_FROM_COURT
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.HmppsDomainEventEmitter.PrisonerReceiveReason.TEMPORARY_ABSENCE_RETURN
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.HmppsDomainEventEmitter.PrisonerReleaseReason.RELEASED
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.HmppsDomainEventEmitter.PrisonerReleaseReason.RELEASED_TO_HOSPITAL
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.HmppsDomainEventEmitter.PrisonerReleaseReason.SENT_TO_COURT
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.HmppsDomainEventEmitter.PrisonerReleaseReason.TEMPORARY_ABSENCE_RELEASE
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.HmppsDomainEventEmitter.PrisonerReleaseReason.TRANSFERRED
import java.time.LocalDate
import java.time.LocalDateTime

private const val OFFENDER_NO = "A9460DY"

@JsonTest
internal class PrisonerMovementsEventServiceTest(@param:Autowired private val objectMapper: ObjectMapper) {
  private val domainEventsEmitter = mock<HmppsDomainEventEmitter>()
  private val telemetryClient = mock<TelemetryClient>()

  private val prisonerMovementsEventService = PrisonerMovementsEventService(domainEventsEmitter, telemetryClient)

  @Test
  internal fun `will not emit anything if changes are not related to movements`() {
    val previousPrisonerSnapshot = prisonerInWithBooking()
    val prisoner = prisonerInWithBooking().apply {
      this.firstName = "BOBBY"
    }

    prisonerMovementsEventService.generateAnyEvents(previousPrisonerSnapshot, prisoner, offenderBooking())

    verifyNoInteractions(domainEventsEmitter)
  }

  @Test
  internal fun `will not emit anything for a new prisoner `() {
    val prisoner = newPrisoner()

    prisonerMovementsEventService.generateAnyEvents(null, prisoner, offenderBooking())

    verifyNoInteractions(domainEventsEmitter)
  }

  @Nested
  inner class OutOnTransfer {
    private val previousPrisonerSnapshot = prisonerBeingTransferred()

    @Test
    internal fun `will emit receive event with reason of transfer`() {
      val prisoner = prisonerTransferredIn("WWI")

      prisonerMovementsEventService.generateAnyEvents(previousPrisonerSnapshot, prisoner, offenderBooking())

      verify(domainEventsEmitter).emitPrisonerReceiveEvent(
        offenderNo = OFFENDER_NO,
        reason = PrisonerReceiveReason.TRANSFERRED,
        prisonId = "WWI",
      )
    }
  }

  @Nested
  inner class OutAtCourt {
    private val previousPrisonerSnapshot = prisonerOutAtCourt()

    @Test
    internal fun `will emit receive event with reason of court return for return to same prison`() {
      val prisoner = prisonerReturnFromCourtSamePrison()

      prisonerMovementsEventService.generateAnyEvents(previousPrisonerSnapshot, prisoner, offenderBooking())

      verify(domainEventsEmitter).emitPrisonerReceiveEvent(
        offenderNo = OFFENDER_NO,
        reason = RETURN_FROM_COURT,
        prisonId = "WWI",
      )
    }

    @Test
    internal fun `will emit receive event with reason of transfer for return to different prison`() {
      val prisoner = prisonerReturnFromCourtDifferentPrison("BXI")

      prisonerMovementsEventService.generateAnyEvents(previousPrisonerSnapshot, prisoner, offenderBooking())

      verify(domainEventsEmitter).emitPrisonerReceiveEvent(
        offenderNo = OFFENDER_NO,
        reason = PrisonerReceiveReason.TRANSFERRED,
        prisonId = "BXI",
      )
    }

    @Test
    internal fun `will emit release event with reason of released when released from court`() {
      val prisoner = releasedPrisoner()

      prisonerMovementsEventService.generateAnyEvents(previousPrisonerSnapshot, prisoner, offenderBooking())

      verify(domainEventsEmitter).emitPrisonerReleaseEvent(
        offenderNo = OFFENDER_NO,
        reason = RELEASED,
        prisonId = "WWI",
      )
    }
  }

  @Nested
  inner class OutOnTAP {
    private val previousPrisonerSnapshot = prisonerOutOnTAP()

    @Test
    internal fun `will emit receive event with reason of TAP return for return to same prison`() {
      val prisoner = prisonerReturnFromTAPSamePrison()

      prisonerMovementsEventService.generateAnyEvents(previousPrisonerSnapshot, prisoner, offenderBooking())

      verify(domainEventsEmitter).emitPrisonerReceiveEvent(
        offenderNo = OFFENDER_NO,
        reason = TEMPORARY_ABSENCE_RETURN,
        prisonId = "WWI",
      )
    }

    @Test
    internal fun `will emit receive event with reason of transfer for return to different prison`() {
      val prisoner = prisonerReturnFromTAPDifferentPrison("BXI")

      prisonerMovementsEventService.generateAnyEvents(previousPrisonerSnapshot, prisoner, offenderBooking())

      verify(domainEventsEmitter).emitPrisonerReceiveEvent(
        offenderNo = OFFENDER_NO,
        reason = PrisonerReceiveReason.TRANSFERRED,
        prisonId = "BXI",
      )
    }

    @Test
    internal fun `will emit release event with reason of released when released from TAP`() {
      val prisoner = releasedPrisoner()

      prisonerMovementsEventService.generateAnyEvents(previousPrisonerSnapshot, prisoner, offenderBooking())

      verify(domainEventsEmitter).emitPrisonerReleaseEvent(
        offenderNo = OFFENDER_NO,
        reason = RELEASED,
        prisonId = "WWI",
      )
    }
  }

  @Nested
  inner class NewOffender {
    private val previousPrisonerSnapshot = newPrisoner()

    @Test
    internal fun `will emit receive event with reason of new admission for new booking`() {
      val prisoner = prisonerInWithBooking("BXI")

      prisonerMovementsEventService.generateAnyEvents(previousPrisonerSnapshot, prisoner, offenderBooking())

      verify(domainEventsEmitter).emitPrisonerReceiveEvent(
        offenderNo = OFFENDER_NO,
        reason = NEW_ADMISSION,
        prisonId = "BXI",
      )
    }
  }

  @Nested
  inner class NewOffenderWithMovedBookings {
    private val previousPrisonerSnapshot = newPrisoner()

    @Test
    internal fun `will emit receive event with reason of new admission for new booking`() {
      val prisoner = prisonerInWithMovedBooking("BXI")

      prisonerMovementsEventService.generateAnyEvents(previousPrisonerSnapshot, prisoner, offenderBooking())

      verify(domainEventsEmitter).emitPrisonerReceiveEvent(
        offenderNo = OFFENDER_NO,
        reason = NEW_ADMISSION,
        prisonId = "BXI",
      )
    }
  }

  @Nested
  inner class MovedBookingsAfterReturnFromCourt {
    private val previousPrisonerSnapshot = releasedPrisoner()

    @Test
    internal fun `will emit receive event with reason of new admission for new booking`() {
      val prisoner = prisonerInWithMovedBooking("BXI").apply { this.lastMovementTypeCode = "CRT" }

      prisonerMovementsEventService.generateAnyEvents(previousPrisonerSnapshot, prisoner, offenderBooking())

      verify(domainEventsEmitter).emitPrisonerReceiveEvent(
        offenderNo = OFFENDER_NO,
        reason = NEW_ADMISSION,
        prisonId = "BXI",
      )
    }
  }

  @Nested
  inner class MovedBookingsAfterReturnFromTAP {
    private val previousPrisonerSnapshot = releasedPrisoner()

    @Test
    internal fun `will emit receive event with reason of new admission for new booking`() {
      val prisoner = prisonerInWithMovedBooking("BXI").apply { this.lastMovementTypeCode = "TAP" }

      prisonerMovementsEventService.generateAnyEvents(previousPrisonerSnapshot, prisoner, offenderBooking())

      verify(domainEventsEmitter).emitPrisonerReceiveEvent(
        offenderNo = OFFENDER_NO,
        reason = NEW_ADMISSION,
        prisonId = "BXI",
      )
    }
  }

  @Nested
  inner class ReleasedOffender {
    private val previousPrisonerSnapshot = releasedPrisoner()

    @Test
    internal fun `will emit receive event with reason of new admission for new booking`() {
      val prisoner = prisonerInWithNewBooking("BXI")

      prisonerMovementsEventService.generateAnyEvents(previousPrisonerSnapshot, prisoner, offenderBooking())

      verify(domainEventsEmitter).emitPrisonerReceiveEvent(
        offenderNo = OFFENDER_NO,
        reason = NEW_ADMISSION,
        prisonId = "BXI",
      )
    }

    @Test
    internal fun `will emit receive event with reason of readmission for existing booking`() {
      val prisoner = recalledPrisoner("BXI")

      prisonerMovementsEventService.generateAnyEvents(previousPrisonerSnapshot, prisoner, offenderBooking())

      verify(domainEventsEmitter).emitPrisonerReceiveEvent(
        offenderNo = OFFENDER_NO,
        reason = READMISSION,
        prisonId = "BXI",
      )
    }

    @Test
    internal fun `will emit receive event with reason of readmission with switch booking to existing old booking`() {
      val prisoner = recalledPrisoner("BXI", bookingId = "99")

      prisonerMovementsEventService.generateAnyEvents(previousPrisonerSnapshot, prisoner, offenderBooking())

      verify(domainEventsEmitter).emitPrisonerReceiveEvent(
        offenderNo = OFFENDER_NO,
        reason = READMISSION_SWITCH_BOOKING,
        prisonId = "BXI",
      )
    }

    @Test
    internal fun `will emit reason of readmission with switch booking even if there was a recent merge`() {
      val prisoner = recalledPrisoner("BXI", bookingId = "99")
      val identifiers = listOf(
        OffenderIdentifier(
          whenCreated = LocalDateTime.now().minusMinutes(2),
          type = "CRO",
          value = "1234",
          issuedAuthorityText = null,
          issuedDate = null,
          offenderId = 1L,
        ),
        OffenderIdentifier(
          whenCreated = LocalDateTime.now().minusMinutes(70),
          type = "MERGED",
          value = "1234",
          issuedAuthorityText = null,
          issuedDate = null,
          offenderId = 1L,
        ),
      )
      val offenderBooking = offenderBooking(identifiers).apply {
        // The admission movement happened after the merge
        this.lastMovementTime = LocalDateTime.now().minusMinutes(69)
      }

      prisonerMovementsEventService.generateAnyEvents(previousPrisonerSnapshot, prisoner, offenderBooking)

      verify(domainEventsEmitter).emitPrisonerReceiveEvent(
        offenderNo = OFFENDER_NO,
        reason = READMISSION_SWITCH_BOOKING,
        prisonId = "BXI",
      )
    }
  }

  @Nested
  inner class MergedOffender {
    private val previousPrisonerSnapshot = newPrisoner()

    @Test
    internal fun `will emit receive event with reason of merge admission for a merged offender record  - merge took place within last 90mins`() {
      val prisoner = prisonerInWithBooking("BXI")
      val identifiers = listOf(
        OffenderIdentifier(
          whenCreated = LocalDateTime.now().minusDays(2),
          type = "CRO",
          value = "1234",
          issuedAuthorityText = null,
          issuedDate = null,
          offenderId = 1L,
        ),
        OffenderIdentifier(
          whenCreated = LocalDateTime.now().minusMinutes(70),
          type = "MERGED",
          value = "1234",
          issuedAuthorityText = null,
          issuedDate = null,
          offenderId = 1L,
        ),
      )

      prisonerMovementsEventService.generateAnyEvents(previousPrisonerSnapshot, prisoner, offenderBooking(identifiers))

      verify(domainEventsEmitter).emitPrisonerReceiveEvent(
        offenderNo = OFFENDER_NO,
        reason = POST_MERGE_ADMISSION,
        prisonId = "BXI",
      )
    }

    @Test
    internal fun `will ignore merge if merge took place over 90 mins ago`() {
      val prisoner = prisonerInWithBooking("BXI")
      val identifiers = listOf(
        OffenderIdentifier(
          whenCreated = LocalDateTime.now().minusMinutes(2),
          type = "CRO",
          value = "1234",
          issuedAuthorityText = null,
          issuedDate = null,
          offenderId = 1L,
        ),
        OffenderIdentifier(
          whenCreated = LocalDateTime.now().minusMinutes(95),
          type = "MERGED",
          value = "1234",
          issuedAuthorityText = null,
          issuedDate = null,
          offenderId = 1L,
        ),
      )

      prisonerMovementsEventService.generateAnyEvents(previousPrisonerSnapshot, prisoner, offenderBooking(identifiers))

      verify(domainEventsEmitter).emitPrisonerReceiveEvent(
        offenderNo = OFFENDER_NO,
        reason = NEW_ADMISSION,
        prisonId = "BXI",
      )
    }

    @Test
    internal fun `will ignore merge if admission took place since the merge`() {
      val prisoner = prisonerInWithBooking("BXI")
      val identifiers = listOf(
        OffenderIdentifier(
          whenCreated = LocalDateTime.now().minusMinutes(2),
          type = "CRO",
          value = "1234",
          issuedAuthorityText = null,
          issuedDate = null,
          offenderId = 1L,
        ),
        OffenderIdentifier(
          whenCreated = LocalDateTime.now().minusMinutes(70),
          type = "MERGED",
          value = "1234",
          issuedAuthorityText = null,
          issuedDate = null,
          offenderId = 1L,
        ),
      )
      val offenderBooking = offenderBooking(identifiers).apply {
        // The admission movement happened after the merge
        this.lastMovementTime = LocalDateTime.now().minusMinutes(69)
      }

      prisonerMovementsEventService.generateAnyEvents(previousPrisonerSnapshot, prisoner, offenderBooking)

      verify(domainEventsEmitter).emitPrisonerReceiveEvent(
        offenderNo = OFFENDER_NO,
        reason = NEW_ADMISSION,
        prisonId = "BXI",
      )
    }

    @Test
    internal fun `will identify merge if merge not the latest identifier type`() {
      val prisoner = prisonerInWithBooking("BXI")
      val identifiers = listOf(
        OffenderIdentifier(
          whenCreated = LocalDateTime.now().minusMinutes(25),
          type = "CRO",
          value = "1234",
          issuedAuthorityText = null,
          issuedDate = null,
          offenderId = 1L,
        ),
        OffenderIdentifier(
          whenCreated = LocalDateTime.now().minusMinutes(45),
          type = "MERGED",
          value = "1234",
          issuedAuthorityText = null,
          issuedDate = null,
          offenderId = 1L,
        ),
      )

      prisonerMovementsEventService.generateAnyEvents(previousPrisonerSnapshot, prisoner, offenderBooking(identifiers))

      verify(domainEventsEmitter).emitPrisonerReceiveEvent(
        offenderNo = OFFENDER_NO,
        reason = POST_MERGE_ADMISSION,
        prisonId = "BXI",
      )
    }

    @Test
    internal fun `will handle no identifiers provided`() {
      val prisoner = prisonerInWithBooking("BXI")

      prisonerMovementsEventService.generateAnyEvents(previousPrisonerSnapshot, prisoner, offenderBooking())

      verify(domainEventsEmitter).emitPrisonerReceiveEvent(
        offenderNo = OFFENDER_NO,
        reason = NEW_ADMISSION,
        prisonId = "BXI",
      )
    }
  }

  @Nested
  inner class CurrentlyInPrison {
    private val previousPrisonerSnapshot = prisonerInWithBooking("BXI")

    @Test
    internal fun `will emit release event with reason of transferred when released to different prison`() {
      val prisoner = prisonerBeingTransferred()

      prisonerMovementsEventService.generateAnyEvents(previousPrisonerSnapshot, prisoner, offenderBooking())

      verify(domainEventsEmitter).emitPrisonerReleaseEvent(
        offenderNo = OFFENDER_NO,
        reason = TRANSFERRED,
        prisonId = "BXI",
      )
    }

    @Test
    internal fun `will emit release event with reason of sent to court when moved to court`() {
      val prisoner = prisonerOutAtCourt()

      prisonerMovementsEventService.generateAnyEvents(previousPrisonerSnapshot, prisoner, offenderBooking())

      verify(domainEventsEmitter).emitPrisonerReleaseEvent(
        offenderNo = OFFENDER_NO,
        reason = SENT_TO_COURT,
        prisonId = "BXI",
      )
    }

    @Test
    internal fun `will emit release event with reason of TAP when released on TAP`() {
      val prisoner = prisonerOutOnTAP()

      prisonerMovementsEventService.generateAnyEvents(previousPrisonerSnapshot, prisoner, offenderBooking())

      verify(domainEventsEmitter).emitPrisonerReleaseEvent(
        offenderNo = OFFENDER_NO,
        reason = TEMPORARY_ABSENCE_RELEASE,
        prisonId = "BXI",
      )
    }

    @Test
    internal fun `will emit release event with reason of released when released from prison`() {
      val prisoner = releasedPrisoner()

      prisonerMovementsEventService.generateAnyEvents(previousPrisonerSnapshot, prisoner, offenderBooking())

      verify(domainEventsEmitter).emitPrisonerReleaseEvent(
        offenderNo = OFFENDER_NO,
        reason = RELEASED,
        prisonId = "BXI",
      )
    }

    @Test
    internal fun `will emit release event with reason of released to hospital when released to hospital`() {
      val prisoner = releasedPrisonerToHospital()

      prisonerMovementsEventService.generateAnyEvents(previousPrisonerSnapshot, prisoner, offenderBooking())

      verify(domainEventsEmitter).emitPrisonerReleaseEvent(
        offenderNo = OFFENDER_NO,
        reason = RELEASED_TO_HOSPITAL,
        prisonId = "BXI",
      )
    }
  }

  private fun newPrisoner() = prisoner("/receive-state-changes/new-prisoner.json")
  private fun prisonerInWithBooking(prisonId: String = "NMI") = prisoner("/receive-state-changes/first-new-booking.json").apply {
    this.prisonId = prisonId
  }

  private fun prisonerInWithMovedBooking(prisonId: String = "NMI") = prisoner("/receive-state-changes/first-moved-booking.json").apply {
    this.prisonId = prisonId
  }

  private fun prisonerInWithNewBooking(prisonId: String = "NMI") = prisoner("/receive-state-changes/second-new-booking.json").apply {
    this.prisonId = prisonId
  }

  private fun releasedPrisoner() = prisoner("/receive-state-changes/released.json")
  private fun recalledPrisoner(prisonId: String = "NMI", bookingId: String = "1203208") = prisoner("/receive-state-changes/recalled.json").apply {
    this.prisonId = prisonId
    this.bookingId = bookingId
  }
  private fun releasedPrisonerToHospital() = prisoner("/receive-state-changes/released-to-hospital.json")

  private fun prisonerBeingTransferred() = prisoner("/receive-state-changes/transfer-out.json")
  private fun prisonerTransferredIn(prisonId: String = "WWI") = prisoner("/receive-state-changes/transfer-in.json").apply {
    this.prisonId = prisonId
  }

  private fun prisonerOutAtCourt() = prisoner("/receive-state-changes/court-out.json")
  private fun prisonerReturnFromCourtSamePrison() = prisoner("/receive-state-changes/court-in-same-prison.json")
  private fun prisonerReturnFromCourtDifferentPrison(prisonId: String = "NMI") = prisoner("/receive-state-changes/court-in-different-prison.json").apply { this.prisonId = prisonId }

  private fun prisonerOutOnTAP() = prisoner("/receive-state-changes/tap-out.json")
  private fun prisonerReturnFromTAPSamePrison() = prisoner("/receive-state-changes/tap-in-same-prison.json")
  private fun prisonerReturnFromTAPDifferentPrison(prisonId: String = "NMI") = prisoner("/receive-state-changes/tap-in-different-prison.json").apply { this.prisonId = prisonId }

  private fun prisoner(resource: String): Prisoner = objectMapper.readValue(resource.readResourceAsText(), Prisoner::class.java)

  private fun offenderBooking(identifiers: List<OffenderIdentifier>? = null) = OffenderBooking(
    "A9460DY",
    1L,
    "BOATENG",
    "AKUSEA",
    LocalDate.of(1976, 5, 15),
    bookingId = 123456L,
    allIdentifiers = identifiers,
  )

  private fun String.readResourceAsText(): String = PrisonerMovementsEventServiceTest::class.java.getResource(this)!!.readText()
}
