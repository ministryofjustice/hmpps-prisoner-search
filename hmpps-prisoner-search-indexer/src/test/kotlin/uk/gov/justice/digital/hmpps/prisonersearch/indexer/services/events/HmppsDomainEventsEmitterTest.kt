package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import software.amazon.awssdk.services.sqs.model.SendMessageResponse
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.DiffCategory.LOCATION
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.DiffProperties
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.HmppsDomainEventEmitter.PrisonerReceiveReason.READMISSION
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.CompletableFuture

/*
 * Most test scenarios are covered by the integration tests in HmppsDomainEventsEmitterIntTest
 */
class HmppsDomainEventsEmitterTest {

  private val objectMapper = ObjectMapper()
  private val hmppsQueueService = mock<HmppsQueueService>()
  private val diffProperties = DiffProperties(prefix = "test.", host = "some_host", events = true)
  private val clock = mock<Clock>()
  private val telemetryClient = mock<TelemetryClient>()
  private val hmppsDomainEventEmitter =
    HmppsDomainEventEmitter(objectMapper, hmppsQueueService, diffProperties, clock, telemetryClient, 0)
  private val publishSqsClient = mock<SqsAsyncClient>()
  private val publishQueue = HmppsQueue("publish", queueName = "some_queue", sqsClient = publishSqsClient)

  @BeforeEach
  fun `setup mocks`() {
    whenever(publishSqsClient.getQueueUrl(any<GetQueueUrlRequest>())).thenReturn(
      CompletableFuture.completedFuture(
        GetQueueUrlResponse.builder().queueUrl("some_url").build(),
      ),
    )
    whenever(hmppsQueueService.findByQueueId(anyString())).thenReturn(publishQueue)
    whenever(publishSqsClient.sendMessage(any<SendMessageRequest>())).thenReturn(
      CompletableFuture.completedFuture(
        SendMessageResponse.builder().build(),
      ),
    )

    Clock.fixed(Instant.parse("2022-09-16T10:40:34Z"), ZoneId.of("UTC")).also {
      whenever(clock.instant()).thenReturn(it.instant())
      whenever(clock.zone).thenReturn(it.zone)
    }
  }

  @Nested
  inner class PrisonerDifferenceEvent {

    @Test
    fun `should not swallow exceptions`() {
      whenever(publishSqsClient.sendMessage(any<SendMessageRequest>())).thenThrow(RuntimeException::class.java)

      assertThatThrownBy {
        hmppsDomainEventEmitter.emitPrisonerDifferenceEvent("some_offender", mapOf(LOCATION to listOf()))
      }.isInstanceOf(RuntimeException::class.java)
    }
  }

  @Nested
  inner class PrisonerCreatedEvent {
    @Test
    fun `should not swallow exceptions`() {
      whenever(publishSqsClient.sendMessage(any<SendMessageRequest>())).thenThrow(RuntimeException::class.java)

      assertThatThrownBy {
        hmppsDomainEventEmitter.emitPrisonerCreatedEvent("some_offender")
      }.isInstanceOf(RuntimeException::class.java)
    }
  }

  @Nested
  inner class PrisonerRemovedEvent {
    @Test
    fun `should not swallow exceptions`() {
      whenever(publishSqsClient.sendMessage(any<SendMessageRequest>())).thenThrow(RuntimeException::class.java)

      assertThatThrownBy {
        hmppsDomainEventEmitter.emitPrisonerRemovedEvent("some_offender")
      }.isInstanceOf(RuntimeException::class.java)
    }
  }

  @Nested
  inner class PrisonerReceivedEvent {
    @Test
    fun `should also log event`() {
      hmppsDomainEventEmitter.emitPrisonerReceiveEvent("some_offender", READMISSION, "MDI")

      verify(telemetryClient).trackEvent(
        eq("test.prisoner-offender-search.prisoner.received"),
        check {
          assertThat(it["eventType"]).isEqualTo("test.prisoner-offender-search.prisoner.received")
          assertThat(it["version"]).isEqualTo("1")
          assertThat(it["description"]).isEqualTo("A prisoner has been received into a prison with reason: re-admission on an existing booking")
          assertThat(it["additionalInformation.nomsNumber"]).isEqualTo("some_offender")
          assertThat(it["additionalInformation.reason"]).isEqualTo("READMISSION")
          assertThat(it["additionalInformation.prisonId"]).isEqualTo("MDI")
        },
        isNull(),
      )
    }

    @Test
    fun `should swallow exceptions and indicate a manual fix is required`() {
      whenever(publishSqsClient.sendMessage(any<SendMessageRequest>())).thenThrow(RuntimeException::class.java)

      hmppsDomainEventEmitter.emitPrisonerReceiveEvent("some_offender", READMISSION, "MDI")
      verify(telemetryClient).trackEvent(
        eq("EVENTS_SEND_FAILURE"),
        check {
          assertThat(it["eventType"]).isEqualTo("prisoner-offender-search.prisoner.received")
          assertThat(it["additionalInformation.nomsNumber"]).isEqualTo("some_offender")
          assertThat(it["additionalInformation.reason"]).isEqualTo("READMISSION")
          assertThat(it["additionalInformation.prisonId"]).isEqualTo("MDI")
        },
        isNull(),
      )
    }
  }

  @Nested
  inner class PrisonerAlertsUpdatedEvent {
    @Test
    fun `should also log event`() {
      hmppsDomainEventEmitter.emitPrisonerAlertsUpdatedEvent("some_offender", "1234567", setOf("XA", "XT"), setOf("ZZ"))

      verify(telemetryClient).trackEvent(
        eq("test.prisoner-offender-search.prisoner.alerts-updated"),
        check {
          assertThat(it["eventType"]).isEqualTo("test.prisoner-offender-search.prisoner.alerts-updated")
          assertThat(it["version"]).isEqualTo("1")
          assertThat(it["description"]).isEqualTo("A prisoner had their alerts updated, added: 2, removed: 1")
          assertThat(it["additionalInformation.nomsNumber"]).isEqualTo("some_offender")
          assertThat(it["additionalInformation.bookingId"]).isEqualTo("1234567")
          assertThat(it["additionalInformation.alertsAdded"]).isEqualTo("[XA, XT]")
          assertThat(it["additionalInformation.alertsRemoved"]).isEqualTo("[ZZ]")
        },
        isNull(),
      )
    }

    @Test
    fun `should swallow exceptions and indicate a manual fix is required`() {
      whenever(publishSqsClient.sendMessage(any<SendMessageRequest>())).thenThrow(RuntimeException::class.java)

      hmppsDomainEventEmitter.emitPrisonerAlertsUpdatedEvent("some_offender", "1234567", setOf("XA"), setOf())

      verify(telemetryClient).trackEvent(
        eq("EVENTS_SEND_FAILURE"),
        check {
          assertThat(it["eventType"]).isEqualTo("prisoner-offender-search.prisoner.alerts-updated")
          assertThat(it["additionalInformation.nomsNumber"]).isEqualTo("some_offender")
          assertThat(it["additionalInformation.bookingId"]).isEqualTo("1234567")
          assertThat(it["additionalInformation.alertsAdded"]).isEqualTo("[XA]")
          assertThat(it["additionalInformation.alertsRemoved"]).isEqualTo("[]")
        },
        isNull(),
      )
    }
  }

  @Nested
  inner class PrisonerConvictedStatusChangedEvent {
    @Test
    fun `should also log event`() {
      hmppsDomainEventEmitter.emitConvictedStatusChangedEvent("some_offender", "1234567", "Convicted")

      verify(telemetryClient).trackEvent(
        eq("test.prisoner-offender-search.prisoner.convicted-status-changed"),
        check {
          assertThat(it["eventType"]).isEqualTo("test.prisoner-offender-search.prisoner.convicted-status-changed")
          assertThat(it["version"]).isEqualTo("1")
          assertThat(it["description"]).isEqualTo("A prisoner had their convicted status changed to Convicted")
          assertThat(it["additionalInformation.nomsNumber"]).isEqualTo("some_offender")
          assertThat(it["additionalInformation.bookingId"]).isEqualTo("1234567")
          assertThat(it["additionalInformation.convictedStatus"]).isEqualTo("Convicted")
        },
        isNull(),
      )
    }

    @Test
    fun `should swallow exceptions and indicate a manual fix is required`() {
      whenever(publishSqsClient.sendMessage(any<SendMessageRequest>())).thenThrow(RuntimeException::class.java)

      hmppsDomainEventEmitter.emitConvictedStatusChangedEvent("some_offender", "1234567", "Convicted")

      verify(telemetryClient).trackEvent(
        eq("EVENTS_SEND_FAILURE"),
        check {
          assertThat(it["eventType"]).isEqualTo("prisoner-offender-search.prisoner.convicted-status-changed")
          assertThat(it["additionalInformation.nomsNumber"]).isEqualTo("some_offender")
          assertThat(it["additionalInformation.bookingId"]).isEqualTo("1234567")
          assertThat(it["additionalInformation.convictedStatus"]).isEqualTo("Convicted")
        },
        isNull(),
      )
    }
  }
}
