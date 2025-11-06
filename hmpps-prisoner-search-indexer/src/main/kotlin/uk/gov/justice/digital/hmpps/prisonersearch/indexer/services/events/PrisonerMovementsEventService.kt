package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.model.nomis.OffenderBooking
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.HmppsDomainEventEmitter.PrisonerReceiveReason.NEW_ADMISSION
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.HmppsDomainEventEmitter.PrisonerReceiveReason.POST_MERGE_ADMISSION
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.HmppsDomainEventEmitter.PrisonerReceiveReason.READMISSION
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.HmppsDomainEventEmitter.PrisonerReceiveReason.READMISSION_SWITCH_BOOKING
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.HmppsDomainEventEmitter.PrisonerReceiveReason.RETURN_FROM_COURT
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.HmppsDomainEventEmitter.PrisonerReceiveReason.TEMPORARY_ABSENCE_RETURN
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.HmppsDomainEventEmitter.PrisonerReceiveReason.TRANSFERRED
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.HmppsDomainEventEmitter.PrisonerReleaseReason
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.HmppsDomainEventEmitter.PrisonerReleaseReason.RELEASED
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.HmppsDomainEventEmitter.PrisonerReleaseReason.RELEASED_TO_HOSPITAL
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.HmppsDomainEventEmitter.PrisonerReleaseReason.SENT_TO_COURT
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.HmppsDomainEventEmitter.PrisonerReleaseReason.TEMPORARY_ABSENCE_RELEASE
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.PossibleMovementChange.MovementInChange
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.PossibleMovementChange.MovementInChange.CourtReturn
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.PossibleMovementChange.MovementInChange.MergeAdmission
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.PossibleMovementChange.MovementInChange.NewAdmission
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.PossibleMovementChange.MovementInChange.Readmission
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.PossibleMovementChange.MovementInChange.TAPReturn
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.PossibleMovementChange.MovementInChange.TransferIn
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.PossibleMovementChange.MovementOutChange.Released
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.PossibleMovementChange.MovementOutChange.ReleasedToHospital
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.PossibleMovementChange.MovementOutChange.SentToCourt
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.PossibleMovementChange.MovementOutChange.TAPRelease
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.PossibleMovementChange.MovementOutChange.TransferOut
import java.time.LocalDateTime

@Service
class PrisonerMovementsEventService(
  private val domainEventEmitter: HmppsDomainEventEmitter,
  private val telemetryClient: TelemetryClient,
) {
  fun generateAnyEvents(
    previousPrisonerSnapshot: Prisoner?,
    prisoner: Prisoner,
    offenderBooking: OffenderBooking,
  ) {
    when (val movementChange = calculateMovementChange(previousPrisonerSnapshot, prisoner, offenderBooking)) {
      PossibleMovementChange.None -> {}
      is MovementInChange ->
        domainEventEmitter.emitPrisonerReceiveEvent(
          offenderNo = movementChange.offenderNo,
          reason = movementChange.reason,
          prisonId = movementChange.prisonId,
        )

      is PossibleMovementChange.MovementOutChange ->
        domainEventEmitter.emitPrisonerReleaseEvent(
          offenderNo = movementChange.offenderNo,
          reason = movementChange.reason,
          prisonId = movementChange.prisonId,
        )
    }
  }

  private fun calculateMovementChange(
    previousPrisonerSnapshot: Prisoner?,
    prisoner: Prisoner,
    offenderBooking: OffenderBooking,
  ): PossibleMovementChange = previousPrisonerSnapshot.let {
    val prisonerNumber = prisoner.prisonerNumber!!
    if (prisoner.isTransferIn(previousPrisonerSnapshot)) {
      TransferIn(prisonerNumber, prisoner.prisonId!!)
    } else if (prisoner.isCourtReturn(previousPrisonerSnapshot)) {
      CourtReturn(prisonerNumber, prisoner.prisonId!!)
    } else if (prisoner.isAdmissionAssociatedWithAMerge(previousPrisonerSnapshot, offenderBooking)) {
      MergeAdmission(prisonerNumber, prisoner.prisonId!!)
    } else if (prisoner.isReadmissionSwitchBooking(previousPrisonerSnapshot)) {
      MovementInChange.ReadmissionSwitchBooking(prisonerNumber, prisoner.prisonId!!)
    } else if (prisoner.isNewAdmission(previousPrisonerSnapshot)) {
      NewAdmission(prisonerNumber, prisoner.prisonId!!)
    } else if (prisoner.isReadmission(previousPrisonerSnapshot)) {
      Readmission(prisonerNumber, prisoner.prisonId!!)
    } else if (prisoner.isTransferViaCourt(previousPrisonerSnapshot)) {
      TransferIn(prisonerNumber, prisoner.prisonId!!)
    } else if (prisoner.isTAPReturn(previousPrisonerSnapshot)) {
      TAPReturn(prisonerNumber, prisoner.prisonId!!)
    } else if (prisoner.isTransferViaTAP(previousPrisonerSnapshot)) {
      TransferIn(prisonerNumber, prisoner.prisonId!!)
    } else if (prisoner.isTransferOut(previousPrisonerSnapshot)) {
      TransferOut(prisonerNumber, previousPrisonerSnapshot?.prisonId!!)
    } else if (prisoner.isCourtOutMovement(previousPrisonerSnapshot)) {
      SentToCourt(prisonerNumber, previousPrisonerSnapshot?.prisonId!!)
    } else if (prisoner.isTAPOutMovement(previousPrisonerSnapshot)) {
      TAPRelease(prisonerNumber, previousPrisonerSnapshot?.prisonId!!)
    } else if (prisoner.isReleaseToHospital(previousPrisonerSnapshot)) {
      ReleasedToHospital(prisonerNumber, previousPrisonerSnapshot?.prisonId!!)
    } else if (prisoner.isRelease(previousPrisonerSnapshot)) {
      Released(prisonerNumber, previousPrisonerSnapshot?.prisonId!!)
    } else if (prisoner.isNewAdmissionDueToMoveBooking(previousPrisonerSnapshot)) {
      NewAdmission(prisonerNumber, prisoner.prisonId!!)
    } else if (prisoner.isAdmissionDueToMoveBooking(previousPrisonerSnapshot)) {
      NewAdmission(prisonerNumber, prisoner.prisonId!!)
    } else if (
      prisoner.isSomeOtherMovementIn(previousPrisonerSnapshot) ||
      prisoner.isSomeOtherMovementOut(previousPrisonerSnapshot)
    ) {
      PossibleMovementChange.None.also {
        // This can happen, so log details as we are not dealing with all scenarios correctly
        mutableMapOf("prisonerNumber" to prisonerNumber)
          .also { eventMap ->
            eventMap.add("this", prisoner)
            eventMap.add("previous", previousPrisonerSnapshot)
            telemetryClient.trackEvent("EVENTS_UNKNOWN_MOVEMENT", eventMap, null)
          }
      }
    } else {
      PossibleMovementChange.None
    }
  }
}

private fun MutableMap<String, String>.add(
  prefix: String,
  prisoner: Prisoner?,
) {
  this[prefix] = UnknownEventData(
    prisoner?.bookingId,
    prisoner?.inOutStatus,
    prisoner?.status,
    prisoner?.lastMovementTypeCode,
    prisoner?.lastMovementReasonCode,
    prisoner?.lastPrisonId,
    prisoner?.recall,
    prisoner?.restrictedPatient,
  ).toString()
}

data class UnknownEventData(
  val bookingId: String?,
  val inOutStatus: String?,
  val status: String?,
  val lastMovementTypeCode: String?,
  val lastMovementReasonCode: String?,
  val lastPrisonId: String?,
  val recall: Boolean?,
  val restrictedPatient: Boolean?,
)

private fun Prisoner.isTransferIn(previousPrisonerSnapshot: Prisoner?) = previousPrisonerSnapshot?.inOutStatus == "TRN" && inOutStatus == "IN"

private fun Prisoner.isTransferOut(previousPrisonerSnapshot: Prisoner?) = previousPrisonerSnapshot?.inOutStatus == "IN" && inOutStatus == "TRN"

private fun Prisoner.isCourtReturn(previousPrisonerSnapshot: Prisoner?) = previousPrisonerSnapshot?.inOutStatus == "OUT" &&
  previousPrisonerSnapshot.lastMovementTypeCode == "CRT" &&
  this.inOutStatus == "IN" &&
  this.lastMovementTypeCode == "CRT"

private fun Prisoner.isCourtOutMovement(previousPrisonerSnapshot: Prisoner?) = previousPrisonerSnapshot?.inOutStatus == "IN" &&
  this.inOutStatus == "OUT" &&
  this.lastMovementTypeCode == "CRT"

private fun Prisoner.isTAPReturn(previousPrisonerSnapshot: Prisoner?) = previousPrisonerSnapshot?.inOutStatus == "OUT" &&
  previousPrisonerSnapshot.lastMovementTypeCode == "TAP" &&
  this.inOutStatus == "IN" &&
  this.lastMovementTypeCode == "TAP"

private fun Prisoner.isTAPOutMovement(previousPrisonerSnapshot: Prisoner?) = previousPrisonerSnapshot?.inOutStatus == "IN" &&
  this.inOutStatus == "OUT" &&
  this.lastMovementTypeCode == "TAP"

private fun Prisoner.isTransferViaCourt(previousPrisonerSnapshot: Prisoner?) = previousPrisonerSnapshot?.inOutStatus == "OUT" &&
  previousPrisonerSnapshot.lastMovementTypeCode == "CRT" &&
  this.inOutStatus == "IN" &&
  this.lastMovementTypeCode == "ADM" &&
  this.lastMovementReasonCode == "TRNCRT"

private fun Prisoner.isTransferViaTAP(previousPrisonerSnapshot: Prisoner?) = previousPrisonerSnapshot?.inOutStatus == "OUT" &&
  previousPrisonerSnapshot.lastMovementTypeCode == "TAP" &&
  this.inOutStatus == "IN" &&
  this.lastMovementTypeCode == "ADM" &&
  this.lastMovementReasonCode == "TRNTAP"

private fun Prisoner.isNewAdmission(previousPrisonerSnapshot: Prisoner?) = this.lastMovementTypeCode == "ADM" &&
  this.status == "ACTIVE IN" &&
  this.bookingId != previousPrisonerSnapshot?.bookingId

private fun Prisoner.isNewAdmissionDueToMoveBooking(previousPrisonerSnapshot: Prisoner?) = previousPrisonerSnapshot?.bookingId == null &&
  this.status == "ACTIVE IN"

private fun Prisoner.isAdmissionDueToMoveBooking(previousPrisonerSnapshot: Prisoner?) = previousPrisonerSnapshot?.status == "INACTIVE OUT" &&
  this.status == "ACTIVE IN"

private fun Prisoner.isReadmission(previousPrisonerSnapshot: Prisoner?) = this.lastMovementTypeCode == "ADM" &&
  this.bookingId == previousPrisonerSnapshot?.bookingId &&
  this.status == "ACTIVE IN" &&
  previousPrisonerSnapshot?.status == "INACTIVE OUT"

private fun Prisoner.isReadmissionSwitchBooking(previousPrisonerSnapshot: Prisoner?) = this.lastMovementTypeCode == "ADM" &&
  previousPrisonerSnapshot?.bookingId != null &&
  this.bookingId != previousPrisonerSnapshot.bookingId &&
  // TODO SDIT-3065 Comparing booking numbers is not good enough to tell us if this is a readmission. See JIRA ticket for edge case details.
  this.bookingId.isBookingBefore(previousPrisonerSnapshot.bookingId) &&
  this.status == "ACTIVE IN" &&
  previousPrisonerSnapshot.status == "INACTIVE OUT"

private fun Prisoner.isRelease(previousPrisonerSnapshot: Prisoner?) = this.lastMovementTypeCode == "REL" &&
  this.status == "INACTIVE OUT" &&
  previousPrisonerSnapshot?.active == true

private fun Prisoner.isReleaseToHospital(previousPrisonerSnapshot: Prisoner?) = this.lastMovementTypeCode == "REL" &&
  this.lastMovementReasonCode == "HP" &&
  this.status == "INACTIVE OUT" &&
  previousPrisonerSnapshot?.active == true

private fun Prisoner.isSomeOtherMovementIn(previousPrisonerSnapshot: Prisoner?) = this.inOutStatus == "IN" &&
  this.status != previousPrisonerSnapshot?.status

private fun Prisoner.isSomeOtherMovementOut(previousPrisonerSnapshot: Prisoner?) = this.inOutStatus == "OUT" &&
  this.status != previousPrisonerSnapshot?.status

private fun Prisoner.isAdmissionAssociatedWithAMerge(
  previousPrisonerSnapshot: Prisoner?,
  offenderBooking: OffenderBooking,
): Boolean = bookingId != previousPrisonerSnapshot?.bookingId &&
  lastMovementTypeCode in listOf("ADM", "TAP", "CRT") &&
  status == "ACTIVE IN" &&
  offenderBooking.identifiersForActiveOffender("MERGED")
    // check the merge is after the admission movement - or if there is no movement then check the merge happened in the last 90 minutes
    ?.any { it.whenCreated > maxOf(offenderBooking.lastMovementTime ?: LocalDateTime.MIN, LocalDateTime.now().minusMinutes(90)) }
    ?: false

private fun String?.isBookingBefore(previousSnapshotBookingId: String?): Boolean = (this?.toLong() ?: Long.MAX_VALUE) < (previousSnapshotBookingId?.toLong() ?: 0)

sealed class PossibleMovementChange {
  sealed class MovementInChange(
    val offenderNo: String,
    val prisonId: String,
    val reason: HmppsDomainEventEmitter.PrisonerReceiveReason,
  ) : PossibleMovementChange() {
    class TransferIn(offenderNo: String, prisonId: String) : MovementInChange(offenderNo, prisonId, TRANSFERRED)
    class CourtReturn(offenderNo: String, prisonId: String) : MovementInChange(offenderNo, prisonId, RETURN_FROM_COURT)
    class TAPReturn(offenderNo: String, prisonId: String) : MovementInChange(offenderNo, prisonId, TEMPORARY_ABSENCE_RETURN)

    class NewAdmission(offenderNo: String, prisonId: String) : MovementInChange(offenderNo, prisonId, NEW_ADMISSION)
    class MergeAdmission(offenderNo: String, prisonId: String) : MovementInChange(offenderNo, prisonId, POST_MERGE_ADMISSION)

    class Readmission(offenderNo: String, prisonId: String) : MovementInChange(offenderNo, prisonId, READMISSION)
    class ReadmissionSwitchBooking(offenderNo: String, prisonId: String) : MovementInChange(offenderNo, prisonId, READMISSION_SWITCH_BOOKING)
  }

  sealed class MovementOutChange(val offenderNo: String, val prisonId: String, val reason: PrisonerReleaseReason) : PossibleMovementChange() {
    class TransferOut(offenderNo: String, prisonId: String) : MovementOutChange(offenderNo, prisonId, PrisonerReleaseReason.TRANSFERRED)

    class TAPRelease(offenderNo: String, prisonId: String) : MovementOutChange(offenderNo, prisonId, TEMPORARY_ABSENCE_RELEASE)

    class Released(offenderNo: String, prisonId: String) : MovementOutChange(offenderNo, prisonId, RELEASED)
    class ReleasedToHospital(offenderNo: String, prisonId: String) : MovementOutChange(offenderNo, prisonId, RELEASED_TO_HOSPITAL)
    class SentToCourt(offenderNo: String, prisonId: String) : MovementOutChange(offenderNo, prisonId, SENT_TO_COURT)
  }

  object None : PossibleMovementChange()
}
