package uk.gov.justice.digital.hmpps.prisonersearchindexer.services

import com.google.gson.Gson
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesResponse
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse
import software.amazon.awssdk.services.sqs.model.QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES
import software.amazon.awssdk.services.sqs.model.QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import software.amazon.awssdk.services.sqs.model.SendMessageResponse
import uk.gov.justice.digital.hmpps.prisonersearchindexer.model.SyncIndex.GREEN
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import java.util.concurrent.CompletableFuture

internal class IndexQueueServiceTest {

  private val hmppsQueueService = mock<HmppsQueueService>()
  private val indexSqsClient = mock<SqsAsyncClient>()
  private val indexSqsDlqClient = mock<SqsAsyncClient>()
  private lateinit var indexQueueService: IndexQueueService

  @BeforeEach
  internal fun setUp() {
    whenever(hmppsQueueService.findByQueueId("index")).thenReturn(HmppsQueue("index", indexSqsClient, "index-queue", indexSqsDlqClient, "index-dlq"))
    whenever(indexSqsClient.getQueueUrl(any<GetQueueUrlRequest>())).thenReturn(CompletableFuture.completedFuture(GetQueueUrlResponse.builder().queueUrl("arn:eu-west-1:index-queue").build()))
    whenever(indexSqsDlqClient.getQueueUrl(any<GetQueueUrlRequest>())).thenReturn(CompletableFuture.completedFuture(GetQueueUrlResponse.builder().queueUrl("arn:eu-west-1:index-dlq").build()))
    indexQueueService = IndexQueueService(hmppsQueueService, gson = Gson())
  }

  @Nested
  inner class SendIndexRequestMessage {
    @BeforeEach
    internal fun setUp() {
      whenever(indexSqsClient.sendMessage(any<SendMessageRequest>())).thenReturn(CompletableFuture.completedFuture(SendMessageResponse.builder().messageId("abc").build()))
      indexQueueService.sendPopulateIndexMessage(GREEN)
    }

    @Test
    fun `will send message with index name`() {
      verify(indexSqsClient).sendMessage(
        check<SendMessageRequest> {
          assertThatJson(it.messageBody()).isEqualTo(
            """{
          "type": "POPULATE_INDEX",
          "index": "GREEN"
          }
            """.trimIndent(),
          )
        },
      )
    }

    @Test
    fun `will send message to index queue`() {
      verify(indexSqsClient).sendMessage(
        check<SendMessageRequest> {
          assertThat(it.queueUrl()).isEqualTo("arn:eu-west-1:index-queue")
        },
      )
    }
  }

  @Nested
  inner class SendPopulatePrisonerPageMessage {
    @BeforeEach
    internal fun setUp() {
      whenever(indexSqsClient.sendMessage(any<SendMessageRequest>())).thenReturn(CompletableFuture.completedFuture(SendMessageResponse.builder().messageId("abc").build()))
      indexQueueService.sendPopulatePrisonerPageMessage(PrisonerPage(1, 1000))
    }

    @Test
    fun `will send message with index name`() {
      verify(indexSqsClient).sendMessage(
        check<SendMessageRequest> {
          assertThatJson(it.messageBody()).isEqualTo(
            """{
          "type": "POPULATE_PRISONER_PAGE",
          "prisonerPage": {
            "page": 1,
            "pageSize": 1000
          }
        }
            """.trimIndent(),
          )
        },
      )
    }

    @Test
    fun `will send message to index queue`() {
      verify(indexSqsClient).sendMessage(
        check<SendMessageRequest> {
          assertThat(it.queueUrl()).isEqualTo("arn:eu-west-1:index-queue")
        },
      )
    }
  }

  @Nested
  inner class SendPopulatePrisonerMessage {
    @BeforeEach
    internal fun setUp() {
      whenever(indexSqsClient.sendMessage(any<SendMessageRequest>())).thenReturn(
        CompletableFuture.completedFuture(SendMessageResponse.builder().messageId("abc").build()),
      )
      indexQueueService.sendPopulatePrisonerMessage("X12345")
    }

    @Test
    fun `will send message with prisonerNumber`() {
      verify(indexSqsClient).sendMessage(
        check<SendMessageRequest> {
          assertThatJson(it.messageBody()).isEqualTo(
            """
        {
          "type":"POPULATE_PRISONER",
          "prisonerNumber":"X12345"
        }
            """.trimIndent(),
          )
        },
      )
    }

    @Test
    fun `will send message to index queue`() {
      verify(indexSqsClient).sendMessage(
        check<SendMessageRequest> {
          assertThat(it.queueUrl()).isEqualTo("arn:eu-west-1:index-queue")
        },
      )
    }
  }

  @Nested
  inner class QueueMessageAttributes {
    @Test
    internal fun `async calls for queue status successfully complete`() {
      val indexQueueResult = GetQueueAttributesResponse.builder().attributes(mapOf(APPROXIMATE_NUMBER_OF_MESSAGES to "7", APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE to "2")).build()
      whenever(indexSqsClient.getQueueAttributes(any<GetQueueAttributesRequest>())).thenReturn(CompletableFuture.completedFuture(indexQueueResult))

      val indexDlqResult = GetQueueAttributesResponse.builder().attributes(mapOf(APPROXIMATE_NUMBER_OF_MESSAGES to "5")).build()
      whenever(indexSqsDlqClient.getQueueAttributes(any<GetQueueAttributesRequest>())).thenReturn(CompletableFuture.completedFuture(indexDlqResult))

      val queueStatus = indexQueueService.getIndexQueueStatus()
      assertThat(queueStatus.messagesOnQueue).isEqualTo(7)
      assertThat(queueStatus.messagesInFlight).isEqualTo(2)
      assertThat(queueStatus.messagesOnDlq).isEqualTo(5)
    }
  }

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  inner class IndexQueueStatusActive {
    private fun activeTestSource() = listOf(
      Arguments.of(0, 0, 0, false),
      Arguments.of(1, 0, 0, true),
      Arguments.of(0, 1, 0, true),
      Arguments.of(0, 0, 1, true),
      Arguments.of(0, 1, 1, true),
      Arguments.of(1, 1, 0, true),
      Arguments.of(0, 1, 1, true),
      Arguments.of(1, 0, 1, true),
      Arguments.of(1, 1, 1, true),
    )

    @ParameterizedTest
    @MethodSource("activeTestSource")
    fun `index queue status active`(messagesOnQueue: Int, messagesOnDlq: Int, messagesInFlight: Int, expectedActive: Boolean) {
      assertThat(IndexQueueStatus(messagesOnQueue, messagesOnDlq, messagesInFlight).active).isEqualTo(expectedActive)
    }
  }
}
