package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration
import org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTestContextBootstrapper
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.BootstrapWith
import org.springframework.web.reactive.config.EnableWebFlux
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.WebClientConfiguration
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.wiremock.AlertsApiExtension
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.wiremock.HmppsAuthApiExtension
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.wiremock.IncentivesApiExtension
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.wiremock.RestrictedPatientsApiExtension
import java.lang.annotation.Inherited
import kotlin.annotation.AnnotationTarget.ANNOTATION_CLASS
import kotlin.annotation.AnnotationTarget.CLASS

/**
 * Annotation for an API service test that focuses **only** on services that call a WebClient
 *
 *
 * Using this annotation will disable full auto-configuration and instead apply only
 *
 */
@Target(ANNOTATION_CLASS, CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Inherited
@ExtendWith(
  IncentivesApiExtension::class,
  RestrictedPatientsApiExtension::class,
  AlertsApiExtension::class,
  HmppsAuthApiExtension::class,
)
@ActiveProfiles("test")
@EnableWebFlux
@SpringBootTest(classes = [WebClientConfiguration::class, WebClientAutoConfiguration::class, OAuth2ClientAutoConfiguration::class, SecurityAutoConfiguration::class, OAuth2ClientWebSecurityAutoConfiguration::class])
@BootstrapWith(SpringBootTestContextBootstrapper::class)
annotation class SpringAPIServiceTest
