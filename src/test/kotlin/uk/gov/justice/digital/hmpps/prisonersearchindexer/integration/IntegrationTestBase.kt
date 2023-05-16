package uk.gov.justice.digital.hmpps.prisonersearchindexer.integration

import com.microsoft.applicationinsights.TelemetryClient
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.prisonersearch.integration.wiremock.HmppsAuthApiExtension
import uk.gov.justice.digital.hmpps.prisonersearch.integration.wiremock.PrisonApiExtension
import uk.gov.justice.digital.hmpps.prisonersearch.integration.wiremock.RestrictedPatientsApiExtension
import uk.gov.justice.digital.hmpps.prisonersearchindexer.integration.wiremock.IncentivesApiExtension

@ExtendWith(
  IncentivesApiExtension::class,
  PrisonApiExtension::class,
  RestrictedPatientsApiExtension::class,
  HmppsAuthApiExtension::class,
)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
abstract class IntegrationTestBase {
  @SpyBean
  lateinit var telemetryClient: TelemetryClient

  @Autowired
  lateinit var webTestClient: WebTestClient
}
