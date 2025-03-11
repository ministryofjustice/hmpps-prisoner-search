package uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners

import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilAsserted
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.SyncIndex
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.PrisonerBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.wiremock.PrisonApiExtension.Companion.prisonApi
import uk.gov.justice.hmpps.sqs.MissingQueueException
import uk.gov.justice.hmpps.sqs.PurgeQueueRequest
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue

class OffenderEventListenerIntTest : IntegrationTestBase() {

  private val hmppsEventsQueue by lazy {
    hmppsQueueService.findByQueueId("hmppseventtestqueue")
      ?: throw MissingQueueException("hmppseventtestqueue queue not found")
  }

  @BeforeEach
  fun purgeHmppsEventsQueue() = purgeDomainQueue()

  private fun purgeDomainQueue() = runTest {
    with(hmppsEventsQueue) {
      hmppsQueueService.purgeQueue(PurgeQueueRequest(queueName, sqsClient, queueUrl))
    }
  }

  private fun getNumberOfMessagesCurrentlyOnDomainQueue(): Int = hmppsEventsQueue.sqsClient.countAllMessagesOnQueue(hmppsEventsQueue.queueUrl).get()
    .also {
      println("Number of messages on queue: $it")
    }

  @Test
  fun `will create index document for a prisoner which does not yet exist when offender event message received`() {
    val bookingId = 12345L
    val prisonerNumber = "O7089FD"
    prisonApi.stubGetNomsNumberForBooking(bookingId, prisonerNumber)
    prisonApi.stubOffenders(PrisonerBuilder(prisonerNumber = prisonerNumber))

    reset(prisonerSpyBeanRepository) // zero the call count

    offenderSqsClient.sendMessage(
      SendMessageRequest.builder().queueUrl(offenderQueueUrl).messageBody(validOffenderBookingChangedMessage(bookingId, "OFFENDER_BOOKING-CHANGED")).build(),
    )

    await untilAsserted {
      val prisoner = prisonerRepository.get(prisonerNumber, listOf(SyncIndex.RED))
      assertThat(prisoner?.prisonerNumber).isEqualTo(prisonerNumber)
      assertThat(getNumberOfMessagesCurrentlyOnDomainQueue()).isEqualTo(2)
    }

    verify(prisonerSpyBeanRepository, times(1)).createPrisoner(any(), eq(SyncIndex.RED))
    verify(prisonerSpyBeanRepository, never()).updateIncentive(eq(prisonerNumber), any(), eq(SyncIndex.RED), any())
  }

  @Test
  fun `will update index for a prisoner which exists when offender event message received`() {
    val prisonerNumber = "O7089FD"
    val bookingId = 12345L
    val prisoner = Prisoner().apply {
      this.prisonerNumber = prisonerNumber
      this.bookingId = bookingId.toString()
      this.inOutStatus = "IN"
      this.status = "ACTIVE IN"
    }
    prisonerRepository.save(prisoner, SyncIndex.RED)

    prisonApi.stubGetNomsNumberForBooking(bookingId, prisonerNumber)
    prisonApi.stubOffenders(PrisonerBuilder(prisonerNumber = prisonerNumber, bookingId = bookingId))

    reset(prisonerSpyBeanRepository) // zero the call count

    offenderSqsClient.sendMessage(
      SendMessageRequest.builder().queueUrl(offenderQueueUrl)
        .messageBody(validOffenderBookingChangedMessage(bookingId, "OFFENDER_BOOKING-CHANGED")).build(),
    )

    await untilAsserted {
      verify(prisonerSpyBeanRepository, never()).createPrisoner(any(), eq(SyncIndex.RED))
      verify(prisonerSpyBeanRepository, never()).updateIncentive(eq(prisonerNumber), any(), eq(SyncIndex.RED), any())
      verify(prisonerSpyBeanRepository, times(1)).updatePrisoner(eq(prisonerNumber), any(), eq(SyncIndex.RED), any())
    }

    await untilAsserted {
      verify(telemetryClient).trackEvent(
        "RED_PRISONER_UPDATED",
        mapOf(
          "prisonerNumber" to prisonerNumber,
          "bookingId" to bookingId.toString(),
          "event" to "OFFENDER_BOOKING-CHANGED",
        ),
        null,
      )
      verify(telemetryClient).trackEvent(eq("test.prisoner-offender-search.prisoner.updated"), any(), isNull())
      // Check that domain events are sent
      assertThat(getNumberOfMessagesCurrentlyOnDomainQueue()).isEqualTo(1)
    }
  }

  @Test
  fun `when nothing has changed, will report as such`() {
    val prisonerNumber = "O7089FD"
    val bookingId = 12345L
    val prisoner = Prisoner().apply {
      this.prisonerNumber = prisonerNumber
      this.bookingId = bookingId.toString()
      this.lastName = "Different"
    }
    prisonerRepository.save(prisoner, SyncIndex.RED)

    prisonApi.stubGetNomsNumberForBooking(bookingId, prisonerNumber)
    prisonApi.stubOffenders(PrisonerBuilder(prisonerNumber = prisonerNumber, bookingId = bookingId))

    // First we update the prisoner to a known state

    offenderSqsClient.sendMessage(
      SendMessageRequest.builder().queueUrl(offenderQueueUrl)
        .messageBody(validOffenderBookingChangedMessage(bookingId, "OFFENDER_BOOKING-CHANGED")).build(),
    )
    await untilAsserted {
      assertThat(getNumberOfMessagesCurrentlyOnDomainQueue()).isEqualTo(1)
      verify(prisonerSpyBeanRepository).updatePrisoner(eq(prisonerNumber), any(), eq(SyncIndex.RED), any())
      verify(telemetryClient).trackEvent(eq("test.prisoner-offender-search.prisoner.updated"), any(), isNull())
      verify(telemetryClient).trackEvent(eq("RED_PRISONER_UPDATED"), any(), isNull())
    }
    reset(prisonerSpyBeanRepository) // zero the call count
    reset(telemetryClient) // zero the call count

    // Now we send the same message again, which should result in no changes

    offenderSqsClient.sendMessage(
      SendMessageRequest.builder().queueUrl(offenderQueueUrl)
        .messageBody(validOffenderBookingChangedMessage(bookingId, "OFFENDER_BOOKING-CHANGED")).build(),
    )

    await untilAsserted {
      verify(telemetryClient).trackEvent(
        "RED_PRISONER_OPENSEARCH_NO_CHANGE",
        mapOf(
          "prisonerNumber" to prisonerNumber,
          "bookingId" to bookingId.toString(),
          "event" to "OFFENDER_BOOKING-CHANGED",
        ),
        null
      )
    }
  }

  @Test
  fun `when only a non-diffable change occurred, no event is generated`() {
    val prisonerNumber = "O7089FD"
    val bookingId = 12345L
    val prisoner = Prisoner().apply {
      this.prisonerNumber = prisonerNumber
      this.bookingId = bookingId.toString()
      this.inOutStatus = "IN"
      this.status = "ACTIVE IN"
    }
    prisonerRepository.save(prisoner, SyncIndex.RED)

    prisonApi.stubGetNomsNumberForBooking(bookingId, prisonerNumber)
    prisonApi.stubOffenders(PrisonerBuilder(prisonerNumber = prisonerNumber, bookingId = bookingId))

    // First we update the prisoner to a known state

    offenderSqsClient.sendMessage(
      SendMessageRequest.builder().queueUrl(offenderQueueUrl)
        .messageBody(validOffenderBookingChangedMessage(bookingId, "OFFENDER_BOOKING-CHANGED")).build(),
    )
    await untilAsserted {
      verify(prisonerSpyBeanRepository).updatePrisoner(eq(prisonerNumber), any(), eq(SyncIndex.RED), any())
      verify(telemetryClient).trackEvent(eq("test.prisoner-offender-search.prisoner.updated"), any(), isNull())
      verify(telemetryClient).trackEvent(eq("RED_PRISONER_UPDATED"), any(), isNull())
      assertThat(getNumberOfMessagesCurrentlyOnDomainQueue()).isEqualTo(1)
    }
    reset(prisonerSpyBeanRepository) // zero the call counts
    reset(telemetryClient)

    // Now we change a non-diffable index field and send the same message again, which should not create an event

    val prisoner2 = prisonerRepository.get(prisonerNumber, listOf(SyncIndex.RED))!!
    prisoner2.pncNumberCanonicalShort = "different"
    prisonerRepository.save(prisoner2, SyncIndex.RED)

    purgeDomainQueue()

    offenderSqsClient.sendMessage(
      SendMessageRequest.builder().queueUrl(offenderQueueUrl)
        .messageBody(validOffenderBookingChangedMessage(bookingId, "OFFENDER_BOOKING-CHANGED")).build(),
    )

    await untilAsserted {
      verify(telemetryClient).trackEvent(eq("RED_PRISONER_UPDATED"), any(), isNull())
    }
    // No domain event should be raised
    assertThat(getNumberOfMessagesCurrentlyOnDomainQueue()).isEqualTo(0)
    verify(telemetryClient, never()).trackEvent(eq("test.prisoner-offender-search.prisoner.updated"), any(), isNull())
  }

  @Test
  fun `will search for all affected prisoners when location update message received`() {
    val prisonerNumber = "O7089FD"
    prisonerRepository.switchAliasIndex(SyncIndex.GREEN)
    prisonerRepository.save(
      Prisoner().apply {
        this.prisonerNumber = prisonerNumber
        this.prisonId = "EWI"
        this.cellLocation = "RES1-2-014"
      },
      SyncIndex.RED,
    )
    prisonerRepository.save(
      Prisoner().apply {
        this.prisonerNumber = "O7089FF"
        this.prisonId = "EWI"
        this.cellLocation = "RES1-2-014"
      },
      SyncIndex.RED,
    )
    prisonerRepository.save(
      Prisoner().apply {
        this.prisonerNumber = "O7089FG"
        this.prisonId = "EWI"
        this.cellLocation = "RES1-1-014" // wrong cell
      },
      SyncIndex.RED,
    )
    prisonerRepository.save(
      Prisoner().apply {
        this.prisonerNumber = "O7089FH"
        this.prisonId = "MDI" // wrong prison
        this.cellLocation = "RES1-2-014"
      },
      SyncIndex.RED,
    )
    await untilCallTo { prisonerRepository.count(SyncIndex.RED) } matches { it == 4L }

    prisonApi.stubOffenders(PrisonerBuilder(prisonerNumber = prisonerNumber))

    offenderSqsClient.sendMessage(
      SendMessageRequest.builder().queueUrl(offenderQueueUrl).messageBody(validPrisonerLocationChangeMessage("AGENCY_INTERNAL_LOCATIONS-UPDATED")).build(),
    )

    await untilAsserted {
      val prisoner = prisonerRepository.get(prisonerNumber, listOf(SyncIndex.RED))
      assertThat(prisoner?.cellLocation).isEqualTo("A-1-1")
      assertThat(getNumberOfMessagesCurrentlyOnDomainQueue()).isEqualTo(2)
    }
    await untilCallTo { prisonApi.countFor("/api/prisoner-search/offenders/$prisonerNumber") } matches { it == 1 }
    await untilCallTo { prisonApi.countFor("/api/prisoner-search/offenders/O7089FF") } matches { it == 1 }
    // check we didn't get extra calls - wrong cell
    await untilCallTo { prisonApi.countFor("/api/prisoner-search/offenders/O7089FG") } matches { it == 0 }
    // check we didn't get extra calls - wrong prison
    await untilCallTo { prisonApi.countFor("/api/prisoner-search/offenders/O7089FH") } matches { it == 0 }
  }

  @Test
  fun `will republish an ASSESSMENT_UPDATED event and then process`() {
    val bookingId = 12349L
    val prisonerNumber = "O7189FD"
    prisonApi.stubGetNomsNumberForBooking(bookingId, prisonerNumber)
    prisonApi.stubOffenders(PrisonerBuilder(prisonerNumber = prisonerNumber))

    offenderSqsClient.sendMessage(
      SendMessageRequest.builder()
        .queueUrl(offenderQueueUrl)
        .messageBody(validOffenderChangedMessage(prisonerNumber, "ASSESSMENT-UPDATED"))
        .build(),
    )

    await untilAsserted {
      val prisoner = prisonerRepository.get(prisonerNumber, listOf(SyncIndex.RED))
      assertThat(prisoner?.prisonerNumber).isEqualTo(prisonerNumber)
      assertThat(getNumberOfMessagesCurrentlyOnDomainQueue()).isEqualTo(2)
    }
  }

  @Test
  fun `will delete merge records and insert new prisoner record on booking number change`() {
    val oldPrisonerNumber = "O7089FE" // record to be removed
    prisonerRepository.save(Prisoner().also { it.prisonerNumber = oldPrisonerNumber }, SyncIndex.RED)
    await untilCallTo { prisonerRepository.count(SyncIndex.RED) } matches { it == 1L }

    val bookingId = 12345L
    val prisonerNumber = "O7089FF" // record to be inserted / updated
    prisonApi.stubGetMergedIdentifiersByBookingId(bookingId, oldPrisonerNumber)
    prisonApi.stubGetNomsNumberForBooking(bookingId, prisonerNumber)
    prisonApi.stubOffenders(PrisonerBuilder(prisonerNumber = prisonerNumber))

    offenderSqsClient.sendMessage(
      SendMessageRequest.builder().queueUrl(offenderQueueUrl).messageBody(validOffenderBookingChangedMessage(bookingId, "BOOKING_NUMBER-CHANGED")).build(),
    )

    await untilAsserted {
      assertThat(prisonerRepository.get(oldPrisonerNumber, listOf(SyncIndex.RED))).isNull()
      assertThat(prisonerRepository.get(prisonerNumber, listOf(SyncIndex.RED))?.prisonerNumber).isEqualTo(prisonerNumber)
      assertThat(getNumberOfMessagesCurrentlyOnDomainQueue()).isEqualTo(2)
    }
  }

  @Test
  fun `will update all indexes when rebuilding index`() {
    val bookingId = 12346L
    val prisonerNumber = "O7089FE"
    prisonApi.stubGetNomsNumberForBooking(bookingId, prisonerNumber)
    prisonApi.stubOffenders(PrisonerBuilder(prisonerNumber = prisonerNumber))

    offenderSqsClient.sendMessage(
      SendMessageRequest.builder().queueUrl(offenderQueueUrl).messageBody(validOffenderBookingChangedMessage(bookingId, "OFFENDER_BOOKING-CHANGED")).build(),
    )

    await untilAsserted {
      assertThat(prisonerRepository.get(prisonerNumber, listOf(SyncIndex.RED))?.prisonerNumber).isEqualTo(prisonerNumber)
      assertThat(getNumberOfMessagesCurrentlyOnDomainQueue()).isEqualTo(2) // created and received events
    }
  }

  private fun validOffenderBookingChangedMessage(bookingId: Long, eventType: String) = validMessage(
    eventType = eventType,
    message = """{\"eventType\":\"$eventType\",\"eventDatetime\":\"2020-03-25T11:24:32.935401\",\"bookingId\":\"$bookingId\",\"nomisEventType\":\"S1_RESULT\"}""",
  )

  private fun validOffenderChangedMessage(prisonerNumber: String, eventType: String) = validMessage(
    eventType = eventType,
    message = """{\"eventType\":\"$eventType\",\"eventDatetime\":\"2020-02-25T11:24:32.935401\",\"offenderIdDisplay\":\"$prisonerNumber\",\"offenderId\":\"2345612\",\"nomisEventType\":\"S1_RESULT\"}""",
  )

  private fun validPrisonerLocationChangeMessage(eventType: String) = validMessage(
    eventType = eventType,
    message = """{\"eventType\":\"$eventType\",\"eventDatetime\":\"2020-02-25T11:24:32.935401\",\"oldDescription\":\"EWI-RES1-2-014\",\"nomisEventType\":\"AGENCY_INTERNAL_LOCATIONS-UPDATED\",\"recordDeleted\":\"false\",\"prisonId\":\"EWI\",\"description\":\"EWI-RES1-2-014\",\"auditModuleName\":\"OIMILOCA\",\"internalLocationId\":\"633621\"}""",
  )

  private fun validMessage(eventType: String, message: String) = """
          {
            "Type": "Notification",
            "MessageId": "20e13002-d1be-56e7-be8c-66cdd7e23341",
            "Message": "$message",
            "MessageAttributes": {
              "eventType": {
                "Type": "String",
                "Value": "$eventType"
              },
              "id": {
                "Type": "String",
                "Value": "cb4645f2-d0c1-4677-806a-8036ed54bf69"
              }
            }
          }
  """.trimIndent()
}
