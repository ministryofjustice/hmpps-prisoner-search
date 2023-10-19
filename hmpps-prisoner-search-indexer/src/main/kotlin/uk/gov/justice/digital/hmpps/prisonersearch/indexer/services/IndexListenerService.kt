package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.TelemetryEvents.MISSING_OFFENDER_ID_DISPLAY
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners.IncentiveChangedMessage
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners.RestrictedPatientMessage
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.dto.nomis.OffenderBooking

@Service
class IndexListenerService(
  private val indexStatusService: IndexStatusService,
  private val prisonerSynchroniserService: PrisonerSynchroniserService,
  private val nomisService: NomisService,
  private val telemetryClient: TelemetryClient,
) {
  fun incentiveChange(message: IncentiveChangedMessage) {
    log.info(
      "Incentive change: {} for prisoner {} with incentive id {}",
      message.description,
      message.additionalInformation.nomsNumber,
      message.additionalInformation.id,
    )
    sync(message.additionalInformation.nomsNumber)
  }
  fun restrictedPatientChange(message: RestrictedPatientMessage) {
    log.info(
      "Restricted patient change: {} for prisoner {}",
      message.description,
      message.additionalInformation.prisonerNumber,
    )
    sync(message.additionalInformation.prisonerNumber)
  }

  fun externalMovement(message: ExternalPrisonerMovementMessage) = sync(message.bookingId)

  fun offenderBookingChange(message: OffenderBookingChangedMessage) = sync(message.bookingId)

  fun offenderBookNumberChange(message: OffenderBookingChangedMessage) =
    message.bookingId.run {
      log.debug("Check for merged booking for ID {}", this)

      // check for merges
      nomisService.getMergedIdentifiersByBookingId(this)?.forEach {
        prisonerSynchroniserService.delete(it.value)
      }

      sync(bookingId = this)
    }

  fun offenderChange(message: OffenderChangedMessage) =
    message.offenderIdDisplay?.run {
      sync(prisonerNumber = this)
    } ?: customEventForMissingOffenderIdDisplay(message)

  fun maybeDeleteOffender(message: OffenderChangedMessage) {
    message.offenderIdDisplay?.run {
      // This event only means that one of potentially several aliases has been deleted
      val offender = nomisService.getOffender(offenderNo = this)
      if (offender == null) {
        log.debug("Delete check: offender ID {} no longer exists, deleting", this)
        prisonerSynchroniserService.delete(prisonerNumber = this)
      } else {
        log.debug("Delete check: offender ID {} still exists, so assuming an alias deletion", this)
        reindexPrisoner(offender)
      }
    } ?: customEventForMissingOffenderIdDisplay(message)
  }

  private fun reindexPrisoner(ob: OffenderBooking): Prisoner? =
    indexStatusService.getIndexStatus()
      .run {
        if (activeIndexesEmpty()) {
          log.info("Ignoring update of prisoner {} as no indexes were active", ob.offenderNo)
          null
        } else {
          prisonerSynchroniserService.reindex(ob, activeIndexes())
        }
      }

  private fun sync(prisonerNumber: String): Prisoner? =
    nomisService.getOffender(prisonerNumber)?.run {
      reindexPrisoner(ob = this)
    } ?: null.also { log.warn("Sync requested for prisoner {} not found", prisonerNumber) }

  private fun sync(bookingId: Long): Prisoner? =
    nomisService.getNomsNumberForBooking(bookingId)?.run {
      sync(prisonerNumber = this)
    } ?: null.also { log.warn("Sync requested for prisoner (by booking id) {} not found", bookingId) }

  private fun customEventForMissingOffenderIdDisplay(
    message: OffenderChangedMessage,
  ): Prisoner? {
    val propertiesMap = mapOf(
      "eventType" to message.eventType,
      "offenderId" to message.offenderId.toString(),
    )

    telemetryClient.trackEvent(MISSING_OFFENDER_ID_DISPLAY, propertiesMap)
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
