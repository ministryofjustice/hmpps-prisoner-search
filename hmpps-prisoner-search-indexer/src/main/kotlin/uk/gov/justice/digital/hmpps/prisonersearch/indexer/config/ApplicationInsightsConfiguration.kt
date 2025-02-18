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
  SWITCH_INDEX,
  POPULATE_PRISONER_PAGES,
  BUILD_INDEX_MSG,
  BUILD_PAGE_MSG,
  BUILD_PAGE_BOUNDARY,
  BUILD_PRISONER_NOT_FOUND,
  COMPARE_INDEX_SIZE,
  COMPARE_INDEX_IDS,
  PRISONER_UPDATED,
  PRISONER_OPENSEARCH_NO_CHANGE,
  PRISONER_DATABASE_NO_CHANGE,
  PRISONER_UPDATED_NO_DIFFERENCES,
  PRISONER_REMOVED,
  PRISONER_CREATED,
  PRISONER_NOT_FOUND,
  INCENTIVE_OPENSEARCH_NO_CHANGE,
  INCENTIVE_CREATED,
  INCENTIVE_UPDATED,
  INCENTIVE_DATABASE_NO_CHANGE,
  RESTRICTED_PATIENT_OPENSEARCH_NO_CHANGE,
  RESTRICTED_PATIENT_UPDATED,
  MISSING_OFFENDER_ID_DISPLAY,
  DIFFERENCE_REPORTED,
  DIFFERENCE_MISSING,
  RED_COMPARE_INDEX_SIZE,
  RED_DIFFERENCE_REPORTED,
  RED_DIFFERENCE_MISSING,
  RED_PRISONER_UPDATED,
  RED_PRISONER_OPENSEARCH_NO_CHANGE,
  EVENTS_UNKNOWN_MOVEMENT,
  EVENTS_SEND_FAILURE,
  INDEX_INCONSISTENCY,
  RED_SIMULATE_PRISONER_DIFFERENCE_EVENT,
  RED_SIMULATE_ALERT_EVENT,
  RED_SIMULATE_MOVEMENT_RECEIVE_EVENT,
  RED_SIMULATE_MOVEMENT_RELEASE_EVENT,
  RED_SIMULATE_PRISONER_CREATED_EVENT,
  RED_SIMULATE_PRISONER_REMOVED_EVENT,
  RED_SIMULATE_CONVICTED_STATUS_CHANGED_EVENT,
}
