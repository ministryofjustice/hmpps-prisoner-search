package uk.gov.justice.digital.hmpps.prisonersearch.indexer.config

import com.microsoft.applicationinsights.TelemetryClient

fun TelemetryClient.trackEvent(event: TelemetryEvents, properties: Map<String, String>) = this.trackEvent(event.name, properties, null)

fun TelemetryClient.trackPrisonerEvent(event: TelemetryEvents, prisonerNumber: String) = this.trackEvent(event.name, mapOf("prisonerNumber" to prisonerNumber), null)

enum class TelemetryEvents {
  BUILDING_INDEX, CANCELLED_BUILDING_INDEX, COMPLETED_BUILDING_INDEX, SWITCH_INDEX,
  POPULATE_PRISONER_PAGES, BUILD_INDEX_MSG, BUILD_PAGE_MSG, BUILD_PAGE_BOUNDARY, BUILD_PRISONER_NOT_FOUND,
  COMPARE_INDEX_SIZE, COMPARE_INDEX_IDS, REFRESH_INDEX_MSG, COMPARE_PAGE_MSG, COMPARE_PAGE_BOUNDARY,
  PRISONER_UPDATED, PRISONER_UPDATED_OS_NO_CHANGE, PRISONER_UPDATED_DB_NO_CHANGE, PRISONER_UPDATED_NO_DIFFERENCES, PRISONER_REMOVED,
  PRISONER_CREATED, PRISONER_NOT_FOUND, MISSING_OFFENDER_ID_DISPLAY,
  DIFFERENCE_REPORTED, DIFFERENCE_MISSING,
}
