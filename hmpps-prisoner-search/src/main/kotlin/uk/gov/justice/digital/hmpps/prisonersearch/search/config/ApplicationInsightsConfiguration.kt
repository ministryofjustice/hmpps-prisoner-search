package uk.gov.justice.digital.hmpps.prisonersearch.search.config

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * TelemetryClient gets altered at runtime by the java agent and so is a no-op otherwise
 */
@Configuration
public open class ApplicationInsightsConfiguration {
  @Bean
  public open fun telemetryClient(): TelemetryClient = TelemetryClient()
}

public fun TelemetryClient.trackEvent(name: String, properties: Map<String, String>) = this.trackEvent(name, properties, null)
