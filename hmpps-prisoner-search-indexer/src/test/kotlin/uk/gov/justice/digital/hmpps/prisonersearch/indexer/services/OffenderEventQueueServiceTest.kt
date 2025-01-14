@file:Suppress("ClassName")

package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.json.JsonTest
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import software.amazon.awssdk.services.sqs.model.SendMessageResponse
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.OffenderEventQueueService.RequeueDestination
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import java.util.concurrent.CompletableFuture

@JsonTest
internal class OffenderEventQueueServiceTest(
  @Autowired val objectMapper: ObjectMapper,
) {
  private val hmppsQueueService = mock<HmppsQueueService>()
  private val offenderEventSqsClient = mock<SqsAsyncClient>()
  private val offenderEventSqsDlqClient = mock<SqsAsyncClient>()
  private val domainEventSqsClient = mock<SqsAsyncClient>()
  private val domainEventSqsDlqClient = mock<SqsAsyncClient>()
  private lateinit var offenderEventQueueService: OffenderEventQueueService

  @BeforeEach
  internal fun setUp() {
    whenever(hmppsQueueService.findByQueueId("offenderqueue")).thenReturn(HmppsQueue("offenderqueue", offenderEventSqsClient, "offender-queue", offenderEventSqsDlqClient, "offender-dlq"))
    whenever(hmppsQueueService.findByQueueId("hmppsdomainqueue")).thenReturn(HmppsQueue("hmppsdomainqueue", domainEventSqsClient, "domain-queue", domainEventSqsDlqClient, "domain-dlq"))
    val offenderQueueRequest = GetQueueUrlRequest.builder().queueName("offender-queue").build()
    val domainQueueRequest = GetQueueUrlRequest.builder().queueName("domain-queue").build()
    val offenderDlqRequest = GetQueueUrlRequest.builder().queueName("offender-dlq").build()
    val domainDlqRequest = GetQueueUrlRequest.builder().queueName("domain-dlq").build()
    whenever(offenderEventSqsClient.getQueueUrl(eq(offenderQueueRequest))).thenReturn(CompletableFuture.completedFuture(GetQueueUrlResponse.builder().queueUrl("arn:eu-west-1:offender-queue").build()))
    whenever(offenderEventSqsClient.getQueueUrl(eq(offenderDlqRequest))).thenReturn(CompletableFuture.completedFuture(GetQueueUrlResponse.builder().queueUrl("arn:eu-west-1:offender-dlq").build()))
    whenever(domainEventSqsClient.getQueueUrl(eq(domainQueueRequest))).thenReturn(CompletableFuture.completedFuture(GetQueueUrlResponse.builder().queueUrl("arn:eu-west-1:domain-queue").build()))
    whenever(domainEventSqsClient.getQueueUrl(eq(domainDlqRequest))).thenReturn(CompletableFuture.completedFuture(GetQueueUrlResponse.builder().queueUrl("arn:eu-west-1:domain-dlq").build()))
    offenderEventQueueService = OffenderEventQueueService(objectMapper, hmppsQueueService, republishDelayInSeconds = 10)
  }

  @Nested
  inner class republishMessageWithDelay {
    @BeforeEach
    internal fun setUp() {
      whenever(offenderEventSqsClient.sendMessage(any<SendMessageRequest>())).thenReturn(CompletableFuture.completedFuture(SendMessageResponse.builder().messageId("abc").build()))
      whenever(domainEventSqsClient.sendMessage(any<SendMessageRequest>())).thenReturn(CompletableFuture.completedFuture(SendMessageResponse.builder().messageId("abc").build()))
    }

    @Test
    fun `will send republish message with delay`() {
      offenderEventQueueService.republishMessageWithDelay("message", "type")
      verify(offenderEventSqsClient).sendMessage(
        check<SendMessageRequest> {
          assertThat(it.messageBody()).isEqualTo("message")
          assertThat(it.delaySeconds()).isEqualTo(10)
        },
      )
    }

    @Test
    fun `will change the event type of the message`() {
      offenderEventQueueService.republishMessageWithDelay("message with an ASSESSMENT of event type ASSESSMENT", "ASSESSMENT")
      verify(offenderEventSqsClient).sendMessage(
        check<SendMessageRequest> {
          assertThat(it.messageBody()).isEqualTo("message with an ASSESSMENT_REPUBLISHED of event type ASSESSMENT_REPUBLISHED")
        },
      )
    }

    @Test
    fun `will send message to offender queue`() {
      offenderEventQueueService.republishMessageWithDelay("message", "type")
      verify(offenderEventSqsClient).sendMessage(
        check<SendMessageRequest> {
          assertThat(it.queueUrl()).isEqualTo("arn:eu-west-1:offender-queue")
        },
      )
    }
  }

  @Nested
  inner class requeueMessageWithDelay {
    @BeforeEach
    internal fun setUp() {
      whenever(offenderEventSqsClient.sendMessage(any<SendMessageRequest>())).thenReturn(CompletableFuture.completedFuture(SendMessageResponse.builder().messageId("abc").build()))
      whenever(domainEventSqsClient.sendMessage(any<SendMessageRequest>())).thenReturn(CompletableFuture.completedFuture(SendMessageResponse.builder().messageId("abc").build()))
    }

    @Test
    fun `will send message with delay`() {
      offenderEventQueueService.requeueMessageWithDelay("message", "type", RequeueDestination.OFFENDER, 2)
      verify(offenderEventSqsClient).sendMessage(
        check<SendMessageRequest> {
          assertThat(it.messageBody()).isEqualTo("message")
          assertThat(it.messageAttributes()["eventType"]?.stringValue()).isEqualTo("type")
          assertThat(it.delaySeconds()).isEqualTo(2)
        },
      )
    }

    @Test
    fun `will send message to offender queue`() {
      offenderEventQueueService.requeueMessageWithDelay("message", "type", RequeueDestination.OFFENDER)
      verify(offenderEventSqsClient).sendMessage(
        check<SendMessageRequest> {
          assertThat(it.queueUrl()).isEqualTo("arn:eu-west-1:offender-queue")
        },
      )
    }

    @Test
    fun `will send message to domain queue`() {
      offenderEventQueueService.requeueMessageWithDelay("message", "type", RequeueDestination.DOMAIN)
      verify(domainEventSqsClient).sendMessage(
        check<SendMessageRequest> {
          assertThat(it.queueUrl()).isEqualTo("arn:eu-west-1:domain-queue")
        },
      )
    }
  }
}
