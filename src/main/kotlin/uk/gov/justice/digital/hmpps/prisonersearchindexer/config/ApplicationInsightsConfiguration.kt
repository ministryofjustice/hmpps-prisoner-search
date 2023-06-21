package uk.gov.justice.digital.hmpps.prisonersearchindexer.config

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * TelemetryClient gets altered at runtime by the java agent and so is a no-op otherwise
 */
@Configuration
class ApplicationInsightsConfiguration {
  @Bean
  fun telemetryClient(): TelemetryClient = TelemetryClient()
}

fun TelemetryClient.trackEvent(event: TelemetryEvents, properties: Map<String, String>) = this.trackEvent(event.name, properties, null)

enum class TelemetryEvents {
  BUILDING_INDEX, CANCELLED_BUILDING_INDEX, COMPLETED_BUILDING_INDEX, SWITCH_INDEX,
  POPULATE_PRISONER_PAGES, PRISONER_UPDATED, BUILD_INDEX_MSG, BUILD_PAGE_MSG, BUILD_PAGE_BOUNDARY,
  COMPARE_INDEX_SIZE, COMPARE_INDEX_IDS, COMPARE_INDEX_FULL
}
