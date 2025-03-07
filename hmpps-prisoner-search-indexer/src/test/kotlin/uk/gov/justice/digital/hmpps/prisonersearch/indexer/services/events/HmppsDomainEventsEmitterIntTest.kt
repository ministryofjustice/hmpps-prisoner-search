@file:OptIn(ExperimentalCoroutinesApi::class)

package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
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
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import software.amazon.awssdk.services.sns.SnsAsyncClient
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest
import software.amazon.awssdk.services.sqs.model.Message
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.DiffCategory.IDENTIFIERS
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.DiffCategory.LOCATION
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.SyncIndex
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.PrisonerBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.readResourceAsText
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.PrisonerDifferenceService
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.wiremock.PrisonApiExtension.Companion.prisonApi
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.wiremock.PrisonApiMockServer
import uk.gov.justice.hmpps.sqs.MissingQueueException
import uk.gov.justice.hmpps.sqs.PurgeQueueRequest
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue
import java.time.Duration

class HmppsDomainEventsEmitterIntTest : IntegrationTestBase() {
  @MockitoSpyBean
  private lateinit var hmppsDomainEventEmitter: HmppsDomainEventEmitter

  @Autowired
  private lateinit var objectMapper: ObjectMapper

  @MockitoSpyBean
  @Qualifier("offenderqueue-sqs-client")
  private lateinit var offenderQueueSqsClient: SqsAsyncClient

  @MockitoSpyBean
  @Qualifier("hmppseventtopic-sns-client")
  private lateinit var hmppsEventTopicSnsClient: SnsAsyncClient

  @MockitoSpyBean
  @Qualifier("publish-sqs-client")
  private lateinit var publishQueueSqsClient: SqsAsyncClient

  @MockitoSpyBean
  private lateinit var prisonerDifferenceService: PrisonerDifferenceService

  private val hmppsEventsQueue by lazy {
    hmppsQueueService.findByQueueId("hmppseventtestqueue")
      ?: throw MissingQueueException("hmppseventtestqueue queue not found")
  }

  @BeforeEach
  fun purgeHmppsEventsQueue() = runTest {
    with(hmppsEventsQueue) {
      hmppsQueueService.purgeQueue(PurgeQueueRequest(queueName, sqsClient, queueUrl))
    }
  }

  @BeforeEach
  fun init() {
    prisonApi.stubOffenders(PrisonerBuilder())
    buildAndSwitchIndex(1)
  }

  @Test
  fun `sends prisoner differences to the domain topic`() {
    hmppsDomainEventEmitter.emitPrisonerDifferenceEvent(
      "some_offender",
      mapOf(IDENTIFIERS to listOf(), LOCATION to listOf()),
      red = true,
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

  private fun getNumberOfMessagesCurrentlyOnDomainQueue(): Int = hmppsEventsQueue.sqsClient.countAllMessagesOnQueue(hmppsEventsQueue.queueUrl).get()

  private fun getNumberOfMessagesCurrentlyOnEventQueue(): Int = offenderQueueSqsClient.countAllMessagesOnQueue(
    offenderQueueUrl,
  ).get()

  @Test
  fun `sends prisoner created events to the domain topic`() {
    hmppsDomainEventEmitter.emitPrisonerCreatedEvent("some_offender", red = true)

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
    hmppsDomainEventEmitter.emitPrisonerRemovedEvent("some_offender", red = true)

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
      red = true,
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
      red = true,
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

    offenderQueueSqsClient.sendMessage(message)
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

    offenderQueueSqsClient.sendMessage(message)
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
    offenderQueueSqsClient.sendMessage(
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
    offenderQueueSqsClient.sendMessage(
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
    recreatePrisoner(PrisonerBuilder(prisonerNumber = "A1239DD", bookingId = 123456, alertCodes = listOf("X" to "XTACT")))

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
                alertCodes = listOf("X" to "XTACT", "W" to "WO"),
              ).toOffenderBooking(),
            ),
        ),
    )
    offenderQueueSqsClient.sendMessage(
      "/messages/offenderAlertsChanged.json".readResourceAsText(),
    )
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
    offenderQueueSqsClient.sendMessage(offenderPhysicalDetailsChanged("A1239DD"))
    await untilCallTo { getNumberOfMessagesCurrentlyOnDomainQueue() } matches { it == 1 }

    assertThatJson(readNextDomainEventMessage()).node("eventType")
      .isEqualTo("test.prisoner-offender-search.prisoner.updated")
  }

  @Test
  fun `e2e - will send prisoner alerts change event to the domain topic when an alert is removed`() {
    recreatePrisoner(
      PrisonerBuilder(
        prisonerNumber = "A1239DD",
        bookingId = 123456,
        alertCodes = listOf("X" to "XTACT", "W" to "WO"),
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
              // technically the alert should be end dated but this will work equally well
              PrisonerBuilder(
                prisonerNumber = "A1239DD",
                bookingId = 123456,
                alertCodes = listOf("W" to "WO"),
              ).toOffenderBooking(),
            ),
        ),
    )
    offenderQueueSqsClient.sendMessage(
      "/messages/offenderAlertsChanged.json".readResourceAsText(),
    )
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
    offenderQueueSqsClient.sendMessage("/messages/offenderDetailsChanged.json".readResourceAsText().replace("A7089FD", "A1239DD"))
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
    offenderQueueSqsClient.sendMessage(message)
    offenderQueueSqsClient.sendMessage(message)
    await untilCallTo { getNumberOfMessagesCurrentlyOnEventQueue() } matches { it == 0 }

    // expecting 2 attempts to update
    await untilAsserted { verify(prisonerSpyBeanRepository, times(2)).updatePrisoner(any(), any(), any(), any()) }

    // expecting 1 update
    await untilAsserted { verify(prisonerDifferenceService).generateDiffEvent<Prisoner>(any(), any(), any(), eq(true)) }

    // but there is only 1 message on the domain queue because the last update was ignored
    await untilAsserted {
      assertThat(getNumberOfMessagesCurrentlyOnDomainQueue()).isEqualTo(1)
    }
  }

  /*
   * This is to test what happens if we fail to send a domain event.
   * In real life:
   * 1. We receive a prison event indicating "something" happened to the prisoner
   * 2. The prisoner is updated in Open Search
   * 3. We update the prisoner event hash to reflect the changes to the prisoner
   * 4. We try to send a domain event BUT IT FAILS
   * 5. The prison event is rejected and is sent to the DLQ
   * 6. The prison event is automatically retried
   * 7. We attempt to update the prisoner event hash again and if successful then send a domain event
   * 8. If the previous update of the prisoner event hash persisted then we can't update it so a domain event would not be sent
   *
   * So this test checks that the prisoner event hash update is rolled back if sending the domain event fails.
   */
//  @Test
//  fun `e2e - should not update prisoner hash if there is an exception when sending the event`() {
//    recreatePrisoner(PrisonerBuilder(prisonerNumber = "A1239DD", bookingId = null))
//
//    val message = "/messages/offenderDetailsChanged.json".readResourceAsText().replace("A7089FD", "A1239DD")
//
//    // remember the prisoner event hash
//    val insertedPrisonerEventHash = prisonerHashRepository.findByIdOrNull("A1239DD")?.prisonerHash
//    assertThat(insertedPrisonerEventHash).isNotNull
//
//    // update the prisoner on ES BUT fail to send an event
//    doThrow(RuntimeException("Failed to send event")).whenever(publishQueueSqsClient)
//      .sendMessage(any<SendMessageRequest>())
//    prisonApi.stubFor(
//      get(urlEqualTo("/api/prisoner-search/offenders/A1239DD"))
//        .willReturn(
//          aResponse()
//            .withHeader("Content-Type", "application/json")
//            .withBody(
//              PrisonerBuilder(
//                prisonerNumber = "A1239DD",
//                bookingId = null,
//                firstName = "NEW_NAME",
//              ).toOffenderBooking(),
//            ),
//        ),
//    )
//    offenderQueueSqsClient.sendMessage(message)
//    await untilCallTo { getNumberOfMessagesCurrentlyOnEventQueue() } matches { it == 0 }
//    await untilAsserted { verify(publishQueueSqsClient).sendMessage(any<SendMessageRequest>()) }
//
//    // The prisoner hash update should have been rolled back
//    val prisonerEventHashAfterAttemptedUpdate =
//      prisonerHashRepository.findByIdOrNull("A1239DD")?.prisonerHash
//    assertThat(prisonerEventHashAfterAttemptedUpdate).isEqualTo(insertedPrisonerEventHash)
//  }
  // TODO: This functionality should be implemented for the RED index

  fun recreatePrisoner(builder: PrisonerBuilder) {
    val prisonerNumber: String = builder.prisonerNumber

    prisonerRepository.delete(prisonerNumber, SyncIndex.RED)

    prisonApi.stubFor(
      get(urlEqualTo("/api/prisoner-search/offenders/$prisonerNumber"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(builder.toOffenderBooking()),
        ),
    )
    offenderQueueSqsClient.sendMessage(
      "/messages/offenderDetailsChanged.json".readResourceAsText().replace("A7089FD", prisonerNumber),
    )

    // create the prisoner in ES
    // 1 call for all indexes
    await untilCallTo { prisonApi.countFor("/api/prisoner-search/offenders/$prisonerNumber") } matches { it == 1 }

    // delete create events
    await atMost Duration.ofSeconds(30) untilCallTo { getNumberOfMessagesCurrentlyOnDomainQueue() } matches { it != 0 }

    await untilCallTo { prisonerRepository.getSummary(prisonerNumber, SyncIndex.RED) } matches { it != null }

    purgeHmppsEventsQueue()

    Mockito.reset(hmppsEventTopicSnsClient)
    Mockito.reset(publishQueueSqsClient)
    Mockito.reset(prisonerDifferenceService)
  }

  private fun readNextDomainEventMessage(): String {
    val updateResult = hmppsEventsQueue.sqsClient.receiveFirstMessage()
    hmppsEventsQueue.sqsClient.deleteLastMessage(updateResult)
    return objectMapper.readValue<MsgBody>(updateResult.body()).Message
  }
  private fun readEventFromNextDomainEventMessage(): String {
    val message = readNextDomainEventMessage()
    return objectMapper.readValue<EventMessage>(message).eventType
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

  private fun SqsAsyncClient.receiveFirstMessage(): Message = receiveMessage(
    ReceiveMessageRequest.builder().queueUrl(hmppsEventsQueue.queueUrl).build(),
  ).get().messages().first()

  private fun SqsAsyncClient.deleteLastMessage(result: Message) = deleteMessage(
    DeleteMessageRequest.builder().queueUrl(hmppsEventsQueue.queueUrl).receiptHandle(result.receiptHandle()).build(),
  ).get()

  private fun SqsAsyncClient.sendMessage(body: String) = sendMessage(
    SendMessageRequest.builder().queueUrl(
      offenderQueueUrl,
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
