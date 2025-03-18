package uk.gov.justice.digital.hmpps.prisonersearch.indexer.config

import com.microsoft.applicationinsights.TelemetryClient

fun TelemetryClient.trackEvent(event: TelemetryEvents, properties: Map<String, String>) = this.trackEvent(event.name, properties, null)

fun TelemetryClient.trackPrisonerEvent(event: TelemetryEvents, prisonerNumber: String) = this.trackEvent(event.name, mapOf("prisonerNumber" to prisonerNumber), null)

fun TelemetryClient.trackPrisonerEvent(
  event: TelemetryEvents,
  prisonerNumber: String,
  bookingId: Long?,
  eventType: String,
) = this.trackEvent(
  event.name,
  mapOf(
    "prisonerNumber" to prisonerNumber,
    "bookingId" to (bookingId?.toString() ?: "not set"),
    "event" to eventType,
  ),
  null,
)

enum class TelemetryEvents {
  BUILDING_INDEX,
  CANCELLED_BUILDING_INDEX,
  COMPLETED_BUILDING_INDEX,
  POPULATE_PRISONER_PAGES,
  BUILD_INDEX_MSG,
  BUILD_PAGE_MSG,
  BUILD_PAGE_BOUNDARY,
  BUILD_PRISONER_NOT_FOUND,
  COMPARE_INDEX_SIZE,
  COMPARE_INDEX_IDS,
  PRISONER_REMOVED,
  PRISONER_NOT_FOUND,
  INCENTIVE_OPENSEARCH_NO_CHANGE,
  INCENTIVE_UPDATED,
  RESTRICTED_PATIENT_OPENSEARCH_NO_CHANGE,
  RESTRICTED_PATIENT_UPDATED,
  MISSING_OFFENDER_ID_DISPLAY,
  DIFFERENCE_REPORTED,
  DIFFERENCE_MISSING,
  PRISONER_UPDATED,
  PRISONER_OPENSEARCH_NO_CHANGE,
  EVENTS_UNKNOWN_MOVEMENT,
  EVENTS_SEND_FAILURE,
  ALERTS_UPDATED,
  ALERTS_OPENSEARCH_NO_CHANGE,
}
