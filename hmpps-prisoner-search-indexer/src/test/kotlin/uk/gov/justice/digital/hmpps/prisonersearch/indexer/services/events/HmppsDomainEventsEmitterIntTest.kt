@file:OptIn(ExperimentalCoroutinesApi::class)

package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.atLeast
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilAsserted
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.doThrow
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import software.amazon.awssdk.services.sns.SnsAsyncClient
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.DiffCategory.IDENTIFIERS
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.DiffCategory.LOCATION
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.PrisonerAlert
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.PrisonerBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.readResourceAsText
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.PrisonerDifferenceService
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.validAlertsMessage
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.wiremock.AlertsApiExtension.Companion.alertsApi
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.wiremock.PrisonApiExtension.Companion.prisonApi
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.wiremock.PrisonApiMockServer
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.wiremock.PrisonRegisterApiExtension
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue
import java.time.Duration

class HmppsDomainEventsEmitterIntTest : IntegrationTestBase() {
  @MockitoSpyBean
  private lateinit var hmppsDomainEventEmitter: HmppsDomainEventEmitter

  @MockitoSpyBean
  @Qualifier("offenderqueue-sqs-client")
  private lateinit var offenderQueueSqsClient: SqsAsyncClient

  @MockitoSpyBean
  @Qualifier("hmppsdomainqueue-sqs-client")
  private lateinit var hmppsDomainQueueSqsClient: SqsAsyncClient

  @MockitoSpyBean
  @Qualifier("hmppseventtopic-sns-client")
  private lateinit var hmppsEventTopicSnsClient: SnsAsyncClient

  @MockitoSpyBean
  @Qualifier("publish-sqs-client")
  private lateinit var publishQueueSqsClient: SqsAsyncClient

  @MockitoSpyBean
  private lateinit var prisonerDifferenceService: PrisonerDifferenceService

  @BeforeEach
  fun init() {
    PrisonRegisterApiExtension.prisonRegisterApi.stubGetPrisons()
    purgeDomainEventsQueue()
    prisonApi.stubOffenders(PrisonerBuilder())
    buildAndSwitchIndex(1)
  }

  @Test
  fun `sends prisoner differences to the domain topic`() {
    hmppsDomainEventEmitter.emitPrisonerDifferenceEvent(
      "some_offender",
      mapOf(IDENTIFIERS to listOf(), LOCATION to listOf()),
    )

    await untilCallTo { getNumberOfMessagesCurrentlyOnDomainQueue() } matches { it!! > 0 }
    await untilCallTo { getNumberOfMessagesCurrentlyOnDomainQueue() } matches { it == 1 }

    val message = readNextDomainEventMessage()

    assertThatJson(message).node("eventType").isEqualTo("test.prisoner-offender-search.prisoner.updated")
    assertThatJson(message).node("version").isEqualTo(1)
    assertThatJson(message).node("occurredAt").isEqualTo("2022-09-16T11:40:34+01:00")
    assertThatJson(message).node("detailUrl").isEqualTo("http://localhost:8080/prisoner/some_offender")
    assertThatJson(message).node("personReference.identifiers").isEqualTo("[{\"type\":\"NOMS\",\"value\":\"some_offender\"}]")
    assertThatJson(message).node("additionalInformation.nomsNumber").isEqualTo("some_offender")
    assertThatJson(message).node("additionalInformation.categoriesChanged").isArray.containsExactlyInAnyOrder(
      "IDENTIFIERS",
      "LOCATION",
    )
  }

  private fun getNumberOfMessagesCurrentlyOnEventQueue(): Int = offenderQueueSqsClient.countAllMessagesOnQueue(
    offenderQueueUrl,
  ).get()

  @Test
  fun `sends prisoner created events to the domain topic`() {
    hmppsDomainEventEmitter.emitPrisonerCreatedEvent("some_offender")

    await untilCallTo { getNumberOfMessagesCurrentlyOnDomainQueue() } matches { it == 1 }

    val result = hmppsEventsQueue.sqsClient.receiveFirstMessage()
    hmppsEventsQueue.sqsClient.deleteLastMessage(result)

    val message: MsgBody = objectMapper.readValue(result.body())

    assertThatJson(message.Message).node("eventType").isEqualTo("test.prisoner-offender-search.prisoner.created")
    assertThatJson(message.Message).node("version").isEqualTo(1)
    assertThatJson(message.Message).node("occurredAt").isEqualTo("2022-09-16T11:40:34+01:00")
    assertThatJson(message.Message).node("detailUrl").isEqualTo("http://localhost:8080/prisoner/some_offender")
    assertThatJson(message.Message).node("additionalInformation.nomsNumber").isEqualTo("some_offender")
    assertThatJson(message.Message).node("personReference.identifiers").isEqualTo("[{\"type\":\"NOMS\",\"value\":\"some_offender\"}]")
  }

  @Test
  fun `sends prisoner removed events to the domain topic`() {
    hmppsDomainEventEmitter.emitPrisonerRemovedEvent("some_offender")

    await untilCallTo { getNumberOfMessagesCurrentlyOnDomainQueue() } matches { it == 1 }

    val result = hmppsEventsQueue.sqsClient.receiveFirstMessage()
    hmppsEventsQueue.sqsClient.deleteLastMessage(result)

    val message: MsgBody = objectMapper.readValue(result.body())

    assertThatJson(message.Message).node("eventType").isEqualTo("test.prisoner-offender-search.prisoner.removed")
    assertThatJson(message.Message).node("version").isEqualTo(1)
    assertThatJson(message.Message).node("occurredAt").isEqualTo("2022-09-16T11:40:34+01:00")
    assertThatJson(message.Message).node("detailUrl").isEqualTo("http://localhost:8080/prisoner/some_offender")
    assertThatJson(message.Message).node("additionalInformation.nomsNumber").isEqualTo("some_offender")
    assertThatJson(message.Message).node("personReference.identifiers").isEqualTo("[{\"type\":\"NOMS\",\"value\":\"some_offender\"}]")
  }

  @Test
  fun `sends prisoner received events to the domain topic`() {
    hmppsDomainEventEmitter.emitPrisonerReceiveEvent(
      "some_offender",
      HmppsDomainEventEmitter.PrisonerReceiveReason.TRANSFERRED,
      "MDI",
    )

    await untilCallTo { getNumberOfMessagesCurrentlyOnDomainQueue() } matches { it == 1 }

    val result = hmppsEventsQueue.sqsClient.receiveFirstMessage()
    hmppsEventsQueue.sqsClient.deleteLastMessage(result)
    val message: MsgBody = objectMapper.readValue(result.body())

    assertThatJson(message.Message).node("eventType").isEqualTo("test.prisoner-offender-search.prisoner.received")
    assertThatJson(message.Message).node("version").isEqualTo(1)
    assertThatJson(message.Message).node("description")
      .isEqualTo("A prisoner has been received into a prison with reason: transfer from another prison")
    assertThatJson(message.Message).node("occurredAt").isEqualTo("2022-09-16T11:40:34+01:00")
    assertThatJson(message.Message).node("detailUrl").isEqualTo("http://localhost:8080/prisoner/some_offender")
    assertThatJson(message.Message).node("additionalInformation.nomsNumber").isEqualTo("some_offender")
    assertThatJson(message.Message).node("additionalInformation.prisonId").isEqualTo("MDI")
    assertThatJson(message.Message).node("additionalInformation.reason").isEqualTo("TRANSFERRED")
    assertThatJson(message.Message).node("personReference.identifiers").isEqualTo("[{\"type\":\"NOMS\",\"value\":\"some_offender\"}]")
  }

  @Test
  fun `sends prisoner released events to the domain topic`() {
    hmppsDomainEventEmitter.emitPrisonerReleaseEvent(
      "some_offender",
      HmppsDomainEventEmitter.PrisonerReleaseReason.TRANSFERRED,
      "MDI",
    )

    await untilCallTo { getNumberOfMessagesCurrentlyOnDomainQueue() } matches { it == 1 }

    val result = hmppsEventsQueue.sqsClient.receiveFirstMessage()
    hmppsEventsQueue.sqsClient.deleteLastMessage(result)
    val message: MsgBody = objectMapper.readValue(result.body())

    assertThatJson(message.Message).node("eventType").isEqualTo("test.prisoner-offender-search.prisoner.released")
    assertThatJson(message.Message).node("version").isEqualTo(1)
    assertThatJson(message.Message).node("description")
      .isEqualTo("A prisoner has been released from a prison with reason: transfer to another prison")
    assertThatJson(message.Message).node("occurredAt").isEqualTo("2022-09-16T11:40:34+01:00")
    assertThatJson(message.Message).node("detailUrl").isEqualTo("http://localhost:8080/prisoner/some_offender")
    assertThatJson(message.Message).node("additionalInformation.nomsNumber").isEqualTo("some_offender")
    assertThatJson(message.Message).node("additionalInformation.prisonId").isEqualTo("MDI")
    assertThatJson(message.Message).node("additionalInformation.reason").isEqualTo("TRANSFERRED")
    assertThatJson(message.Message).node("personReference.identifiers").isEqualTo("[{\"type\":\"NOMS\",\"value\":\"some_offender\"}]")
  }

  @Test
  fun `e2e - will send prisoner updated event to the domain topic`() {
    recreatePrisoner(PrisonerBuilder(prisonerNumber = "A1239DD", bookingId = 123456))

    // update the prisoner on ES
    prisonApi.stubFor(
      get(urlEqualTo("/api/prisoner-search/offenders/A1239DD"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              PrisonerBuilder(
                prisonerNumber = "A1239DD",
                bookingId = 123456,
                firstName = "NEW_NAME",
              ).toOffenderBooking(),
            ),
        ),
    )
    val message = "/messages/offenderDetailsChanged.json".readResourceAsText().replace("A7089FD", "A1239DD")

    offenderQueueSqsClient.sendOffenderMessage(message)
    await untilCallTo { getNumberOfMessagesCurrentlyOnEventQueue() } matches { it == 0 }
    await untilCallTo { getNumberOfMessagesCurrentlyOnDomainQueue() } matches { it == 1 }

    val updateMsgBody = readNextDomainEventMessage()
    assertThatJson(updateMsgBody).node("eventType").isEqualTo("test.prisoner-offender-search.prisoner.updated")
    assertThatJson(updateMsgBody).node("additionalInformation.nomsNumber").isEqualTo("A1239DD")
    assertThatJson(updateMsgBody).node("additionalInformation.categoriesChanged").isArray.containsExactlyInAnyOrder("PERSONAL_DETAILS")
  }

  @Test
  fun `e2e - will delay sending domain events to the topic`() {
    recreatePrisoner(PrisonerBuilder(prisonerNumber = "A1239DD", bookingId = 123456))

    // update the prisoner on ES
    prisonApi.stubFor(
      get(urlEqualTo("/api/prisoner-search/offenders/A1239DD"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              PrisonerBuilder(
                prisonerNumber = "A1239DD",
                bookingId = 123456,
                firstName = "NEW_NAME",
              ).toOffenderBooking(),
            ),
        ),
    )
    val message = "/messages/offenderDetailsChanged.json".readResourceAsText().replace("A7089FD", "A1239DD")

    offenderQueueSqsClient.sendOffenderMessage(message)
    await atLeast Duration.ofSeconds(1) untilCallTo { getNumberOfMessagesCurrentlyOnDomainQueue() } matches { it == 1 }

    val updateMsgBody = readNextDomainEventMessage()
    assertThatJson(updateMsgBody).node("eventType").isEqualTo("test.prisoner-offender-search.prisoner.updated")
  }

  @Test
  fun `e2e - will send prisoner release event to the domain topic`() {
    recreatePrisoner(PrisonerBuilder(prisonerNumber = "A1239DD", bookingId = null))

    // update the prisoner on ES
    prisonApi.stubFor(
      get(urlEqualTo("/api/prisoner-search/offenders/A1239DD"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              PrisonerBuilder(
                prisonerNumber = "A1239DD",
                bookingId = null,
                released = true,
              ).toOffenderBooking(),
            ),
        ),
    )
    offenderQueueSqsClient.sendOffenderMessage(
      "/messages/offenderDetailsChanged.json".readResourceAsText().replace("A7089FD", "A1239DD"),
    )
    await atMost Duration.ofSeconds(30) untilCallTo { getNumberOfMessagesCurrentlyOnDomainQueue() } matches { it == 2 }
    val nextTwoEventTypes = listOf(readEventFromNextDomainEventMessage(), readEventFromNextDomainEventMessage())

    assertThat(nextTwoEventTypes).containsExactlyInAnyOrder(
      "test.prisoner-offender-search.prisoner.updated",
      "test.prisoner-offender-search.prisoner.released",
    )
  }

  @Test
  fun `e2e - will send prisoner received event to the domain topic`() {
    recreatePrisoner(PrisonerBuilder(prisonerNumber = "A1239DD", bookingId = null, released = true))

    // update the prisoner on ES
    prisonApi.stubFor(
      get(urlEqualTo("/api/prisoner-search/offenders/A1239DD"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              PrisonerBuilder(
                prisonerNumber = "A1239DD",
                bookingId = null,
                released = false,
              ).toOffenderBooking(),
            ),
        ),
    )
    offenderQueueSqsClient.sendOffenderMessage(
      "/messages/offenderDetailsChanged.json".readResourceAsText().replace("A7089FD", "A1239DD"),
    )
    await atMost Duration.ofSeconds(30) untilCallTo { getNumberOfMessagesCurrentlyOnDomainQueue() } matches { it == 2 }

    val nextTwoEventTypes = listOf(readEventFromNextDomainEventMessage(), readEventFromNextDomainEventMessage())

    assertThat(nextTwoEventTypes).containsExactlyInAnyOrder(
      "test.prisoner-offender-search.prisoner.updated",
      "test.prisoner-offender-search.prisoner.received",
    )
  }

  @Test
  fun `e2e - will send prisoner alerts change event to the domain topic when an alert is added`() {
    // Create the prisoner on ES
    Prisoner()
      .apply {
        prisonerNumber = "A1239DD"
        alerts = listOf(
          PrisonerAlert(
            alertCode = "XTACT",
            alertType = "X",
            active = true,
            expired = false,
          ),
        )
      }
      .also { prisonerRepository.save(it) }

    alertsApi.stubSuccess("A1239DD", listOf("X" to "XTACT", "W" to "WO"))

    hmppsDomainQueueSqsClient.sendDomainMessage(validAlertsMessage("A1239DD", "person.alerts.changed"))
    await untilCallTo { getNumberOfMessagesCurrentlyOnDomainQueue() } matches { it == 2 }

    val nextTwoEventTypes = listOf(readEventFromNextDomainEventMessage(), readEventFromNextDomainEventMessage())

    assertThat(nextTwoEventTypes).containsExactlyInAnyOrder(
      "test.prisoner-offender-search.prisoner.updated",
      "test.prisoner-offender-search.prisoner.alerts-updated",
    )
  }

  @Test
  fun `e2e - will send prisoner physical details change event to the domain topic when changes are made`() {
    recreatePrisoner(PrisonerBuilder(prisonerNumber = "A1239DD", bookingId = 123456, heightCentimetres = 200))

    // update the prisoner on ES
    prisonApi.stubOffenderNoFromBookingId("A1239DD")
    prisonApi.stubFor(
      get(urlEqualTo("/api/prisoner-search/offenders/A1239DD"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              PrisonerBuilder(
                prisonerNumber = "A1239DD",
                bookingId = 123456,
                heightCentimetres = 190,
              ).toOffenderBooking(),
            ),
        ),
    )
    offenderQueueSqsClient.sendOffenderMessage(offenderPhysicalDetailsChanged("A1239DD"))
    await untilCallTo { getNumberOfMessagesCurrentlyOnDomainQueue() } matches { it == 1 }

    assertThatJson(readNextDomainEventMessage()).node("eventType")
      .isEqualTo("test.prisoner-offender-search.prisoner.updated")
  }

  @Test
  fun `e2e - will send prisoner alerts change event to the domain topic when an alert is removed`() {
    // Create the prisoner on ES
    Prisoner()
      .apply {
        prisonerNumber = "A1239DD"
        alerts = listOf(
          PrisonerAlert(
            alertCode = "XTACT",
            alertType = "X",
            active = true,
            expired = false,
          ),
          PrisonerAlert(
            alertCode = "WO",
            alertType = "W",
            active = true,
            expired = false,
          ),
        )
      }
      .also { prisonerRepository.save(it) }

    alertsApi.stubSuccess("A1239DD", listOf("W" to "WO"))

    hmppsDomainQueueSqsClient.sendDomainMessage(validAlertsMessage("A1239DD", "person.alerts.changed"))

    await untilCallTo { getNumberOfMessagesCurrentlyOnDomainQueue() } matches { it == 2 }

    val nextTwoEventTypes = listOf(readEventFromNextDomainEventMessage(), readEventFromNextDomainEventMessage())

    assertThat(nextTwoEventTypes).containsExactlyInAnyOrder(
      "test.prisoner-offender-search.prisoner.updated",
      "test.prisoner-offender-search.prisoner.alerts-updated",
    )
  }

  @Test
  fun `e2e - will send prisoner convicted status change event to the domain topic when a convictedStatus change occurs`() {
    recreatePrisoner(
      PrisonerBuilder(
        prisonerNumber = "A1239DD",
        bookingId = 123456,
        convictedStatus = "Convicted",
      ),
    )

    // update the prisoner on ES
    prisonApi.stubOffenderNoFromBookingId("A1239DD")
    prisonApi.stubFor(
      get(urlEqualTo("/api/prisoner-search/offenders/A1239DD"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              PrisonerBuilder(
                prisonerNumber = "A1239DD",
                bookingId = 123456,
                convictedStatus = "Remand",
              ).toOffenderBooking(),
            ),
        ),
    )
    offenderQueueSqsClient.sendOffenderMessage("/messages/offenderDetailsChanged.json".readResourceAsText().replace("A7089FD", "A1239DD"))
    await untilCallTo { getNumberOfMessagesCurrentlyOnDomainQueue() } matches { it == 2 }

    val nextTwoEventTypes = listOf(readEventFromNextDomainEventMessage(), readEventFromNextDomainEventMessage())

    assertThat(nextTwoEventTypes).containsExactlyInAnyOrder(
      "test.prisoner-offender-search.prisoner.updated",
      "test.prisoner-offender-search.prisoner.convicted-status-changed",
    )
  }

  @Test
  fun `e2e - will send single prisoner updated event for 2 identical updates`() {
    recreatePrisoner(PrisonerBuilder(prisonerNumber = "A1239DD", bookingId = 123456))

    val message = "/messages/offenderDetailsChanged.json".readResourceAsText().replace("A7089FD", "A1239DD")

    // update the prisoner on ES - TWICE
    prisonApi.stubFor(
      get(urlEqualTo("/api/prisoner-search/offenders/A1239DD"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              PrisonerBuilder(
                prisonerNumber = "A1239DD",
                bookingId = 123456,
                firstName = "NEW_NAME",
              ).toOffenderBooking(),
            ),
        ),
    )
    offenderQueueSqsClient.sendOffenderMessage(message)
    offenderQueueSqsClient.sendOffenderMessage(message)
    await untilCallTo { getNumberOfMessagesCurrentlyOnEventQueue() } matches { it == 0 }

    // expecting 2 attempts to update
    await untilAsserted { verify(prisonerSpyBeanRepository, times(2)).updatePrisoner(any(), any(), any()) }

    // expecting 1 update
    await untilAsserted { verify(prisonerDifferenceService).generateDiffEvent<Prisoner>(any(), any(), any()) }

    // but there is only 1 message on the domain queue because the last update was ignored
    await untilAsserted {
      assertThat(getNumberOfMessagesCurrentlyOnDomainQueue()).isEqualTo(1)
    }
  }

  @Test
  fun `send failure is retried`() {
    doThrow(RuntimeException::class.java)
      .doCallRealMethod()
      .whenever(publishQueueSqsClient).sendMessage(any<SendMessageRequest>())

    hmppsDomainEventEmitter.emitPrisonerRemovedEvent("some_offender")

    await atLeast Duration.ofSeconds(1) untilCallTo { getNumberOfMessagesCurrentlyOnDomainQueue() } matches { it == 1 }

    val result = hmppsEventsQueue.sqsClient.receiveFirstMessage()
    hmppsEventsQueue.sqsClient.deleteLastMessage(result)

    assertThat(result.messageId()).isNotNull()
    verify(publishQueueSqsClient, times(2)).sendMessage(any<SendMessageRequest>())

//    val message: MsgBody = objectMapper.readValue(result.body())
//
//    assertThatJson(message.Message).node("eventType").isEqualTo("test.prisoner-offender-search.prisoner.removed")
//    assertThatJson(message.Message).node("version").isEqualTo(1)
//    assertThatJson(message.Message).node("occurredAt").isEqualTo("2022-09-16T11:40:34+01:00")
//    assertThatJson(message.Message).node("detailUrl").isEqualTo("http://localhost:8080/prisoner/some_offender")
//    assertThatJson(message.Message).node("additionalInformation.nomsNumber").isEqualTo("some_offender")
//    assertThatJson(message.Message).node("personReference.identifiers").isEqualTo("[{\"type\":\"NOMS\",\"value\":\"some_offender\"}]")
  }

  fun recreatePrisoner(builder: PrisonerBuilder) {
    val prisonerNumber: String = builder.prisonerNumber

    prisonerRepository.delete(prisonerNumber)

    prisonApi.stubFor(
      get(urlEqualTo("/api/prisoner-search/offenders/$prisonerNumber"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(builder.toOffenderBooking()),
        ),
    )
    offenderQueueSqsClient.sendOffenderMessage(
      "/messages/offenderDetailsChanged.json".readResourceAsText().replace("A7089FD", prisonerNumber),
    )

    // create the prisoner in ES
    // 1 call for all indexes
    await untilCallTo { prisonApi.countFor("/api/prisoner-search/offenders/$prisonerNumber") } matches { it == 1 }

    // Wait for create and received events (and possibly others), then delete them
    var numberToExpect = 1 // created
    if (!builder.released) numberToExpect++ // received
    if (builder.convictedStatus != null) numberToExpect++ // convicted status changed
    await atMost Duration.ofSeconds(30) untilCallTo { getNumberOfMessagesCurrentlyOnDomainQueue() } matches { it == numberToExpect }

    await untilCallTo { prisonerRepository.getSummary(prisonerNumber) } matches { it != null }

    purgeDomainEventsQueue()

    Mockito.reset(hmppsEventTopicSnsClient)
    Mockito.reset(publishQueueSqsClient)
    Mockito.reset(prisonerDifferenceService)
  }

  private fun PrisonApiMockServer.stubOffenderNoFromBookingId(prisonerNumber: String) {
    this.stubFor(
      get(WireMock.urlPathMatching("/api/bookings/\\d*"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(PrisonerBuilder(prisonerNumber = prisonerNumber, bookingId = null).toOffenderBooking()),
        ),
    )
  }

  private fun SqsAsyncClient.sendOffenderMessage(body: String) = sendMessage(
    SendMessageRequest.builder().queueUrl(
      offenderQueueUrl,
    ).messageBody(body).build(),
  ).get()

  private fun SqsAsyncClient.sendDomainMessage(body: String) = sendMessage(
    SendMessageRequest.builder().queueUrl(
      hmppsDomainQueueUrl,
    ).messageBody(body).build(),
  ).get()

  private fun offenderPhysicalDetailsChanged(offenderNumber: String) =
    """
    {
  "Type": "Notification",
  "MessageId": "20e13002-d1be-56e7-be8c-66cdd7e23341",
  "TopicArn": "arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-f221e27fcfcf78f6ab4f4c3cc165eee7",
  "Message": "{\"eventType\":\"OFFENDER_PHYSICAL_DETAILS-CHANGED\",\"eventDatetime\":\"2020-02-25T11:24:32.935401\",\"offenderIdDisplay\":\"$offenderNumber\",\"nomisEventType\":\"OFFENDER_PHYSICAL_DETAILS-CHANGED\"}",
  "Timestamp": "2020-02-25T11:25:16.169Z",
  "SignatureVersion": "1",
  "Signature": "h5p3FnnbsSHxj53RFePh8HR40cbVvgEZa6XUVTlYs/yuqfDsi17MPA+bX4ijKmmTT2l6xG2xYhcmRAbJWQ4wrwncTBm2azgiwSO5keRNWYVdiC0rI484KLZboP1SDsE+Y7hOU/R0dz49q7+0yd+QIocPteKB/8xG7/6kjGStAZKf3cEdlxOwLhN+7RU1Yk2ENuwAJjVRtvlAa76yKB3xvL2hId7P7ZLmHGlzZDNZNYxbg9C8HGxteOzZ9ZeeQsWDf9jmZ+5+7dKXQoW9LeqwHxEAq2vuwSZ8uwM5JljXbtS5w1P0psXPYNoin2gU1F5MDK8RPzjUtIvjINx08rmEOA==",
  "SigningCertURL": "https://sns.eu-west-2.amazonaws.com/SimpleNotificationService-a86cb10b4e1f29c941702d737128f7b6.pem",
  "UnsubscribeURL": "https://sns.eu-west-2.amazonaws.com/?Action=Unsubscribe&SubscriptionArn=arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-f221e27fcfcf78f6ab4f4c3cc165eee7:92545cfe-de5d-43e1-8339-c366bf0172aa",
  "MessageAttributes": {
    "eventType": {
      "Type": "String",
      "Value": "OFFENDER_PHYSICAL_DETAILS-CHANGED"
    },
    "id": {
      "Type": "String",
      "Value": "cb4645f2-d0c1-4677-806a-8036ed54bf69"
    },
    "contentType": {
      "Type": "String",
      "Value": "text/plain;charset=UTF-8"
    },
    "timestamp": {
      "Type": "Number.java.lang.Long",
      "Value": "1582629916147"
    }
  }
}
    """.trimIndent()
}

@JsonNaming(value = PropertyNamingStrategies.UpperCamelCaseStrategy::class)
data class MsgBody(val Message: String)

data class EventMessage(val eventType: String)
