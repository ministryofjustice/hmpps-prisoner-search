package uk.gov.justice.digital.hmpps.prisonersearch.indexer.config

import com.microsoft.applicationinsights.TelemetryClient

/**
 * TelemetryClient gets altered at runtime by the java agent and so is a no-op otherwise
 */
fun TelemetryClient.trackEvent(event: uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.TelemetryEvents, properties: Map<String, String>) = this.trackEvent(event.name, properties, null)
fun TelemetryClient.trackPrisonerEvent(event: uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.TelemetryEvents, prisonerNumber: String) = this.trackEvent(event.name, mapOf("prisonerNumber" to prisonerNumber), null)

enum class TelemetryEvents {
  BUILDING_INDEX, CANCELLED_BUILDING_INDEX, COMPLETED_BUILDING_INDEX, SWITCH_INDEX,
  POPULATE_PRISONER_PAGES, BUILD_INDEX_MSG, BUILD_PAGE_MSG, BUILD_PAGE_BOUNDARY, BUILD_PRISONER_NOT_FOUND,
  COMPARE_INDEX_SIZE, COMPARE_INDEX_IDS, COMPARE_INDEX_FULL,
  PRISONER_UPDATED, PRISONER_REMOVED, PRISONER_NOT_FOUND,
}
