package uk.gov.justice.digital.hmpps.prisonersearchindexer.services

import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.autoconfigure.security.oauth2.client.reactive.ReactiveOAuth2ClientAutoConfiguration
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTestContextBootstrapper
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.BootstrapWith
import org.springframework.web.reactive.config.EnableWebFlux
import uk.gov.justice.digital.hmpps.prisonersearchindexer.config.WebClientConfiguration
import uk.gov.justice.digital.hmpps.prisonersearchindexer.integration.wiremock.HmppsAuthApiExtension
import uk.gov.justice.digital.hmpps.prisonersearchindexer.integration.wiremock.IncentivesApiExtension
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
  HmppsAuthApiExtension::class,
)
@ActiveProfiles("test")
@EnableWebFlux
@SpringBootTest(classes = [WebClientConfiguration::class, WebClientAutoConfiguration::class, ReactiveOAuth2ClientAutoConfiguration::class, ReactiveSecurityAutoConfiguration::class, ReactiveOAuth2ClientAutoConfiguration::class])
@BootstrapWith(SpringBootTestContextBootstrapper::class)
annotation class SpringAPIServiceTest
