package uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners

import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.test.runTest
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import software.amazon.awssdk.services.sns.SnsAsyncClient
import software.amazon.awssdk.services.sns.model.PublishRequest
import software.amazon.awssdk.services.sns.model.PublishResponse
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.IntegrationTestBase
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.MissingQueueException
import uk.gov.justice.hmpps.sqs.PurgeQueueRequest
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue
import java.util.concurrent.CompletableFuture

class DomainEventPublisherListenerTest : IntegrationTestBase() {

  @MockitoSpyBean
  @Qualifier("hmppseventtopic-sns-client")
  private lateinit var hmppsEventTopicSnsClient: SnsAsyncClient

  @Autowired
  private lateinit var objectMapper: ObjectMapper

  private val publishQueue by lazy { hmppsQueueService.findByQueueId("publish") as HmppsQueue }
  private val publishSqsClient by lazy { publishQueue.sqsClient }
  private val publishQueueUrl by lazy { publishQueue.queueUrl }

  private val hmppsEventsQueue by lazy {
    hmppsQueueService.findByQueueId("hmppseventtestqueue")
      ?: throw MissingQueueException("hmppseventtestqueue queue not found")
  }

  private val domainEventPublishRequest by lazy {
    SendMessageRequest.builder().queueUrl(publishQueueUrl)
      .messageBody(
        objectMapper.writeValueAsString(
          DomainEvent(
            eventType = "myevent",
            body = objectMapper.writeValueAsString(MyEvent()),
          ),
        ),
      )
      .build()
  }

  @BeforeEach
  fun purgeHmppsEventsQueue() = runTest {
    with(hmppsEventsQueue) {
      hmppsQueueService.purgeQueue(PurgeQueueRequest(queueName, sqsClient, queueUrl))
    }
  }

  private fun getNumberOfMessagesCurrentlyOnDomainQueue(): Int? =
    hmppsEventsQueue.sqsClient.countAllMessagesOnQueue(hmppsEventsQueue.queueUrl).get()

  @Test
  fun `can publish a message`() {
    publishSqsClient.sendMessage(domainEventPublishRequest)

    await untilCallTo { getNumberOfMessagesCurrentlyOnDomainQueue() } matches { it == 1 }
  }

  @Test
  fun `can publish a message by retrying even if publish method fails once`() {
    doThrow(
      RuntimeException("Oh no!"),
    ).doCallRealMethod().whenever(hmppsEventTopicSnsClient).publish(any<PublishRequest>())

    publishSqsClient.sendMessage(domainEventPublishRequest)

    await untilCallTo { getNumberOfMessagesCurrentlyOnDomainQueue() } matches { it == 1 }
  }

  @Test
  fun `can publish a message by retying even if publish returns a failed promise once`() {
    doReturn(
      CompletableFuture.failedFuture<PublishResponse>(
        RuntimeException("Oh no!"),
      ),
    ).doCallRealMethod().whenever(hmppsEventTopicSnsClient).publish(any<PublishRequest>())

    publishSqsClient.sendMessage(domainEventPublishRequest)

    await untilCallTo { getNumberOfMessagesCurrentlyOnDomainQueue() } matches { it == 1 }
  }
}

data class MyEvent(val attribute: String = "DUMMY")
