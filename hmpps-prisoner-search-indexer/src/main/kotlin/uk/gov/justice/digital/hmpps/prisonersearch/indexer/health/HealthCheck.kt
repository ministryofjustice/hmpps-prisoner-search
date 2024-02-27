package uk.gov.justice.digital.hmpps.prisonersearch.indexer.health

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.hmpps.kotlin.health.HealthPingCheck

@Component("hmppsAuth")
class AuthHealth(@Qualifier("hmppsAuthHealthWebClient") webClient: WebClient) : HealthPingCheck(webClient)

@Component("prisonApi")
class PrisonApiHealth(@Qualifier("prisonApiHealthWebClient") webClient: WebClient) : HealthPingCheck(webClient)

@Component("incentivesApi")
class IncentivesApiHealth(@Qualifier("incentivesHealthWebClient") webClient: WebClient) : HealthPingCheck(webClient)

@Component("restrictedPatientsApi")
class RestrictedPatientsApiHealth(@Qualifier("restrictedPatientsHealthWebClient") webClient: WebClient) : HealthPingCheck(webClient)
