package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.TelemetryEvents.MISSING_OFFENDER_ID_DISPLAY
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners.AlertEvent
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners.IncentiveChangedMessage
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners.RestrictedPatientMessage
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.HmppsDomainEventEmitter

@Service
class IndexListenerService(
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
    prisonerSynchroniserService.reindexIncentive(message.additionalInformation.nomsNumber, eventType)
  }

  fun restrictedPatientChange(message: RestrictedPatientMessage, eventType: String) {
    log.info(
      "Restricted patient change: {} for prisoner {}",
      message.description,
      message.additionalInformation.prisonerNumber,
    )
    reindexRestrictedPatient(message.additionalInformation.prisonerNumber, eventType)
  }

  fun alertChange(message: AlertEvent, eventType: String) {
    message.personReference.findNomsNumber()?.also { prisonerNumber ->
      log.info(
        "Alert change: {} for prisoner {}",
        message.description,
        prisonerNumber,
      )
      prisonerSynchroniserService.reindexAlerts(prisonerNumber, eventType)
    }
      ?: throw IllegalStateException("Alert event found with no prisonerNumber: uuid = " + message.additionalInformation.alertUuid)
  }

  fun externalMovement(message: ExternalPrisonerMovementMessage, eventType: String) = sync(message.bookingId, eventType)

  fun offenderBookingChange(message: OffenderBookingChangedMessage, eventType: String): Prisoner? = sync(message.bookingId, eventType)

  fun offenderBookNumberChange(message: OffenderBookingChangedMessage, eventType: String) = message.bookingId.run {
    log.debug("Check for merged booking for ID {}", this)

    // check for merges
    nomisService.getMergedIdentifiersByBookingId(this)?.forEach {
      prisonerSynchroniserService.delete(it.value)
    }

    sync(bookingId = this, eventType)
  }

  fun offenderChange(message: OffenderChangedMessage, eventType: String) = message.offenderIdDisplay?.run {
    sync(prisonerNumber = this, eventType)
  } ?: customEventForMissingOffenderIdDisplay(eventType, message.offenderId)

  fun maybeDeleteOffender(message: OffenderChangedMessage, eventType: String) {
    message.offenderIdDisplay?.run {
      // This event only means that one of potentially several aliases has been deleted
      val offender = nomisService.getOffender(offenderNo = this)
      if (offender == null) {
        log.debug("Delete check: offender ID {} no longer exists, deleting", this)
        prisonerSynchroniserService.delete(prisonerNumber = this)
        hmppsDomainEventEmitter.emitPrisonerRemovedEvent(offenderNo = this)
      } else {
        log.debug("Delete check: offender ID {} still exists, so assuming an alias deletion", this)
        prisonerSynchroniserService.reindexUpdate(offender, eventType)
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

  private fun reindexRestrictedPatient(prisonerNumber: String, eventType: String) {
    nomisService.getOffender(prisonerNumber)?.let { ob ->
      prisonerSynchroniserService.reindexRestrictedPatient(prisonerNumber, ob, eventType)
    }
  }

  private fun sync(prisonerNumber: String, eventType: String): Prisoner? = nomisService.getOffender(prisonerNumber)?.run {
    prisonerSynchroniserService.reindexUpdate(ob = this, eventType = eventType)
  } ?: null.also { log.warn("Sync requested for prisoner {} not found", prisonerNumber) }

  private fun sync(bookingId: Long, eventType: String): Prisoner? = nomisService.getNomsNumberForBooking(bookingId)?.run {
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
