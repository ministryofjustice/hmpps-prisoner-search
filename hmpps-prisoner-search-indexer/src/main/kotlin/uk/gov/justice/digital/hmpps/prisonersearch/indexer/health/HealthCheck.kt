package uk.gov.justice.digital.hmpps.prisonersearch.indexer.health

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.hmpps.kotlin.health.HealthPingCheck

@Component("hmppsAuth")
class AuthHealth(@Qualifier("hmppsAuthHealthWebClient") webClient: WebClient) : HealthPingCheck(webClient)

@Component("nomisApi")
class NomisApiHealth(@Qualifier("nomisApiHealthWebClient") webClient: WebClient) : HealthPingCheck(webClient)

@Component("prisonApi")
class PrisonApiHealth(@Qualifier("prisonApiHealthWebClient") webClient: WebClient) : HealthPingCheck(webClient)

@Component("incentivesApi")
class IncentivesApiHealth(@Qualifier("incentivesHealthWebClient") webClient: WebClient) : HealthPingCheck(webClient)

@Component("restrictedPatientsApi")
class RestrictedPatientsApiHealth(@Qualifier("restrictedPatientsHealthWebClient") webClient: WebClient) : HealthPingCheck(webClient)

@Component("alertsApi")
class AlertsApiHealth(@Qualifier("alertsHealthWebClient") webClient: WebClient) : HealthPingCheck(webClient)

@Component("complexityOfNeedApi")
class ComplexityOfNeedApiHealth(@Qualifier("complexityOfNeedHealthWebClient") webClient: WebClient) : HealthPingCheck(webClient)

@Component("prisonRegisterApi")
class PrisonRegisterApiHealth(@Qualifier("prisonRegisterWebClient") webClient: WebClient) : HealthPingCheck(webClient)
