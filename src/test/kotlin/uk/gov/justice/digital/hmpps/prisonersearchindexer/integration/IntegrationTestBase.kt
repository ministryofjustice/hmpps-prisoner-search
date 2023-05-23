package uk.gov.justice.digital.hmpps.prisonersearchindexer.integration

import com.microsoft.applicationinsights.TelemetryClient
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.prisonersearchindexer.integration.wiremock.HmppsAuthApiExtension
import uk.gov.justice.digital.hmpps.prisonersearchindexer.integration.wiremock.IncentivesApiExtension
import uk.gov.justice.digital.hmpps.prisonersearchindexer.integration.wiremock.PrisonApiExtension
import uk.gov.justice.digital.hmpps.prisonersearchindexer.integration.wiremock.RestrictedPatientsApiExtension
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.MissingQueueException

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

  @SpyBean
  lateinit var hmppsQueueService: HmppsQueueService

  protected val indexQueue by lazy { hmppsQueueService.findByQueueId("indexqueue") ?: throw MissingQueueException("HmppsQueue indexqueue not found") }
  protected val hmppsEventTopic by lazy { hmppsQueueService.findByTopicId("hmppseventtopic") ?: throw MissingQueueException("HmppsTopic hmpps event topic not found") }

  val indexQueueName by lazy { indexQueue.queueName }
  val indexDlqName by lazy { indexQueue.dlqName as String }
  val hmppsEventTopicName by lazy { hmppsEventTopic.arn }
}
