package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.SyncIndex
import uk.gov.justice.digital.hmpps.prisonersearch.common.nomis.OffenderBooking
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.TelemetryEvents.MISSING_OFFENDER_ID_DISPLAY
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners.IncentiveChangedMessage
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners.RestrictedPatientMessage
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.HmppsDomainEventEmitter

@Service
class IndexListenerService(
  private val indexStatusService: IndexStatusService,
  private val prisonerSynchroniserService: PrisonerSynchroniserService,
  private val nomisService: NomisService,
  private val prisonerLocationService: PrisonerLocationService,
  private val telemetryClient: TelemetryClient,
  private val hmppsDomainEventEmitter: HmppsDomainEventEmitter,
) {
  fun incentiveChange(message: IncentiveChangedMessage, eventType: String) {
    log.info(
      "Incentive change: {} for prisoner {} with incentive id {}",
      message.description,
      message.additionalInformation.nomsNumber,
      message.additionalInformation.id,
    )
    syncGreenBlue(message.additionalInformation.nomsNumber, eventType)
    reindexIncentive(message.additionalInformation.nomsNumber, eventType)
  }

  fun restrictedPatientChange(message: RestrictedPatientMessage, eventType: String) {
    log.info(
      "Restricted patient change: {} for prisoner {}",
      message.description,
      message.additionalInformation.prisonerNumber,
    )
    syncGreenBlue(message.additionalInformation.prisonerNumber, eventType)
    reindexRestrictedPatient(message.additionalInformation.prisonerNumber, eventType)
  }

  fun externalMovement(message: ExternalPrisonerMovementMessage, eventType: String) = syncBoth(message.bookingId, eventType)

  fun offenderBookingChange(message: OffenderBookingChangedMessage, eventType: String): Prisoner? = syncBoth(message.bookingId, eventType)

  fun offenderBookNumberChange(message: OffenderBookingChangedMessage, eventType: String) = message.bookingId.run {
    log.debug("Check for merged booking for ID {}", this)

    // check for merges
    nomisService.getMergedIdentifiersByBookingId(this)?.forEach {
      prisonerSynchroniserService.delete(it.value)
    }

    syncBoth(bookingId = this, eventType)
  }

  fun offenderChange(message: OffenderChangedMessage, eventType: String) = message.offenderIdDisplay?.run {
    syncBoth(prisonerNumber = this, eventType)
  } ?: customEventForMissingOffenderIdDisplay(eventType, message.offenderId)

  fun maybeDeleteOffender(message: OffenderChangedMessage, eventType: String) {
    message.offenderIdDisplay?.run {
      // This event only means that one of potentially several aliases has been deleted
      val offender = nomisService.getOffender(offenderNo = this)
      if (offender == null) {
        log.debug("Delete check: offender ID {} no longer exists, deleting", this)
        prisonerSynchroniserService.delete(prisonerNumber = this)
        hmppsDomainEventEmitter.emitPrisonerRemovedEvent(offenderNo = this, red = false)
        hmppsDomainEventEmitter.emitPrisonerRemovedEvent(offenderNo = this, red = true)
      } else {
        log.debug("Delete check: offender ID {} still exists, so assuming an alias deletion", this)
        reindexPrisonerBoth(offender, eventType)
      }
    } ?: customEventForMissingOffenderIdDisplay(eventType, message.offenderId)
  }

  fun offenderBookingReassigned(message: OffenderBookingReassignedMessage, eventType: String) {
    message.offenderIdDisplay?.run {
      syncBoth(prisonerNumber = this, eventType)
    } ?: customEventForMissingOffenderIdDisplay(eventType, message.offenderId)

    // also sync the previous offender if it is different
    message.previousOffenderIdDisplay?.run {
      if (this != message.offenderIdDisplay) {
        syncBoth(prisonerNumber = this, eventType)
      }
    } ?: customEventForMissingOffenderIdDisplay(eventType, message.previousOffenderId)
  }

  fun prisonerLocationChange(message: PrisonerLocationChangedMessage, eventType: String) {
    if (message.oldDescription == null) {
      log.info("Ignoring location change for {} as no old description", message.prisonId)
    } else {
      // need to search for all prisoners that have the old description
      val cellLocation = message.oldDescription.substringAfter("${message.prisonId}-")
      prisonerLocationService.findPrisoners(message.prisonId, cellLocation).forEach {
        syncBoth(prisonerNumber = it, eventType)
      }
    }
  }

  private fun reindexPrisonerBoth(ob: OffenderBooking, eventType: String): Prisoner? = indexStatusService.getIndexStatus()
    .run {
      if (activeIndexesEmpty()) {
        log.info("Ignoring update of prisoner {} as no indexes were active", ob.offenderNo)
        null
      } else {
        prisonerSynchroniserService.reindexUpdate(ob, eventType)
        prisonerSynchroniserService.reindex(ob, activeIndexes(), eventType)
      }
    }

  private fun reindexPrisonerGreenBlue(ob: OffenderBooking, eventType: String): Prisoner? = indexStatusService.getIndexStatus()
    .run {
      if (activeIndexesEmpty()) {
        log.info("Ignoring update (old) of prisoner {} as no indexes were active", ob.offenderNo)
        null
      } else {
        prisonerSynchroniserService.reindex(ob, activeIndexes(), eventType)
      }
    }

  private fun reindexPrisonerRed(ob: OffenderBooking, eventType: String) = indexStatusService.getIndexStatus()
    .run {
      if (activeIndexesEmpty()) {
        log.info("Ignoring update of (new) prisoner {} as no indexes were active", ob.offenderNo)
      } else {
        prisonerSynchroniserService.reindexUpdate(ob, eventType)
      }
    }

  private fun reindexIncentive(prisonerNumber: String, eventType: String) {
    indexStatusService.getIndexStatus()
      .run {
        if (activeIndexesEmpty()) {
          log.info("Ignoring update of incentive for {} as no indexes were active", prisonerNumber)
          null
        } else {
          prisonerSynchroniserService.reindexIncentive(prisonerNumber, SyncIndex.RED, eventType)
        }
      }
  }

  private fun reindexRestrictedPatient(prisonerNumber: String, eventType: String) {
    indexStatusService.getIndexStatus()
      .run {
        if (activeIndexesEmpty()) {
          log.info("Ignoring update of RestrictedPatient for {} as no indexes were active", prisonerNumber)
          null
        } else {
          nomisService.getOffender(prisonerNumber)?.let { ob ->
            prisonerSynchroniserService.reindexRestrictedPatient(prisonerNumber, ob, SyncIndex.RED, eventType)
          }
        }
      }
  }

  /**
   * Sync both the old green/blue and new red indices
   */
  private fun syncBoth(prisonerNumber: String, eventType: String): Prisoner? = nomisService.getOffender(prisonerNumber)?.run {
    reindexPrisonerRed(ob = this, eventType)
    reindexPrisonerGreenBlue(ob = this, eventType)
  } ?: null.also { log.warn("Sync requested for prisoner {} not found", prisonerNumber) }

  private fun syncGreenBlue(prisonerNumber: String, eventType: String): Prisoner? = nomisService.getOffender(prisonerNumber)?.run {
    reindexPrisonerGreenBlue(ob = this, eventType)
  } ?: null.also { log.warn("Sync (old) requested for prisoner {} not found", prisonerNumber) }

  private fun syncBoth(bookingId: Long, eventType: String): Prisoner? = nomisService.getNomsNumberForBooking(bookingId)?.run {
    syncBoth(prisonerNumber = this, eventType)
  } ?: null.also { log.warn("Sync requested for prisoner (by booking id) {} not found", bookingId) }

  private fun customEventForMissingOffenderIdDisplay(
    eventType: String,
    offenderId: Long,
  ): Prisoner? {
    mapOf(
      "eventType" to eventType,
      "offenderId" to offenderId.toString(),
    ).apply {
      telemetryClient.trackEvent(MISSING_OFFENDER_ID_DISPLAY, this)
    }
    return null
  }

  private companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}

data class ExternalPrisonerMovementMessage(val bookingId: Long)

data class OffenderBookingChangedMessage(val bookingId: Long)

data class OffenderChangedMessage(
  val eventType: String,
  val offenderId: Long,
  val offenderIdDisplay: String?,
)

data class OffenderBookingReassignedMessage(
  val bookingId: Long,
  val offenderId: Long,
  val offenderIdDisplay: String?,
  val previousOffenderId: Long,
  val previousOffenderIdDisplay: String?,
)

data class PrisonerLocationChangedMessage(
  val prisonId: String,
  val oldDescription: String?,
)
