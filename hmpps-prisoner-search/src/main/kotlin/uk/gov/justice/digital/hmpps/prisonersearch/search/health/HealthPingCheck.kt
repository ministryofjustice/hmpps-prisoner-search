package uk.gov.justice.digital.hmpps.prisonersearch.search.health

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.hmpps.kotlin.health.HealthPingCheck

@Component("hmppsAuth")
class HmppsAuthHealthPing(@Qualifier("hmppsAuthHealthWebClient") webClient: WebClient) : HealthPingCheck(webClient)

@Component("alertsApi")
class AlertsApiHealthPing(@Qualifier("alertsApiHealthWebClient") webClient: WebClient) : HealthPingCheck(webClient)
