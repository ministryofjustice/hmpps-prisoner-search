package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.common.nomis.OffenderBooking
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.TelemetryEvents.MISSING_OFFENDER_ID_DISPLAY
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners.IncentiveChangedMessage
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners.RestrictedPatientMessage

@Service
class IndexListenerService(
  private val indexStatusService: IndexStatusService,
  private val prisonerSynchroniserService: PrisonerSynchroniserService,
  private val nomisService: NomisService,
  private val prisonerLocationService: PrisonerLocationService,
  private val telemetryClient: TelemetryClient,
) {
  fun incentiveChange(message: IncentiveChangedMessage, eventType: String) {
    log.info(
      "Incentive change: {} for prisoner {} with incentive id {}",
      message.description,
      message.additionalInformation.nomsNumber,
      message.additionalInformation.id,
    )
    sync(message.additionalInformation.nomsNumber, eventType)
  }
  fun restrictedPatientChange(message: RestrictedPatientMessage, eventType: String) {
    log.info(
      "Restricted patient change: {} for prisoner {}",
      message.description,
      message.additionalInformation.prisonerNumber,
    )
    sync(message.additionalInformation.prisonerNumber, eventType)
  }

  fun externalMovement(message: ExternalPrisonerMovementMessage, eventType: String) = sync(message.bookingId, eventType)

  fun offenderBookingChange(message: OffenderBookingChangedMessage, eventType: String) = sync(message.bookingId, eventType)

  fun offenderBookNumberChange(message: OffenderBookingChangedMessage, eventType: String) =
    message.bookingId.run {
      log.debug("Check for merged booking for ID {}", this)

      // check for merges
      nomisService.getMergedIdentifiersByBookingId(this)?.forEach {
        prisonerSynchroniserService.delete(it.value)
      }

      sync(bookingId = this, eventType)
    }

  fun offenderChange(message: OffenderChangedMessage, eventType: String) =
    message.offenderIdDisplay?.run {
      sync(prisonerNumber = this, eventType)
    } ?: customEventForMissingOffenderIdDisplay(eventType, message.offenderId)

  fun maybeDeleteOffender(message: OffenderChangedMessage, eventType: String) {
    message.offenderIdDisplay?.run {
      // This event only means that one of potentially several aliases has been deleted
      val offender = nomisService.getOffender(offenderNo = this)
      if (offender == null) {
        log.debug("Delete check: offender ID {} no longer exists, deleting", this)
        prisonerSynchroniserService.delete(prisonerNumber = this)
      } else {
        log.debug("Delete check: offender ID {} still exists, so assuming an alias deletion", this)
        reindexPrisoner(offender, eventType)
      }
    } ?: customEventForMissingOffenderIdDisplay(eventType, message.offenderId)
  }

  fun offenderBookingReassigned(message: OffenderBookingReassignedMessage, eventType: String) {
    message.offenderIdDisplay?.run {
      sync(prisonerNumber = this, eventType)
    } ?: customEventForMissingOffenderIdDisplay(eventType, message.offenderId)

    // also sync the previous offender if it is different
    message.previousOffenderIdDisplay?.run {
      if (this != message.offenderIdDisplay) {
        sync(prisonerNumber = this, eventType)
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
        sync(prisonerNumber = it, eventType)
      }
    }
  }

  private fun reindexPrisoner(ob: OffenderBooking, eventType: String): Prisoner? =
    indexStatusService.getIndexStatus()
      .run {
        if (activeIndexesEmpty()) {
          log.info("Ignoring update of prisoner {} as no indexes were active", ob.offenderNo)
          null
        } else {
          prisonerSynchroniserService.reindex(ob, activeIndexes(), eventType)
        }
      }

  private fun sync(prisonerNumber: String, eventType: String): Prisoner? =
    nomisService.getOffender(prisonerNumber)?.run {
      reindexPrisoner(ob = this, eventType)
    } ?: null.also { log.warn("Sync requested for prisoner {} not found", prisonerNumber) }

  private fun sync(bookingId: Long, eventType: String): Prisoner? =
    nomisService.getNomsNumberForBooking(bookingId)?.run {
      sync(prisonerNumber = this, eventType)
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
