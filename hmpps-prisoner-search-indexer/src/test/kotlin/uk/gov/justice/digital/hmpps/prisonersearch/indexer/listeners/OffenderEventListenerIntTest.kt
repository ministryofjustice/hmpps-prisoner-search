package uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilAsserted
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IndexState.BUILDING
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IndexState.COMPLETED
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IndexStatus
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.SyncIndex
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.PrisonerBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.wiremock.PrisonApiExtension.Companion.prisonApi

class OffenderEventListenerIntTest : IntegrationTestBase() {
  @Test
  fun `will index a prisoner when offender event message received`() {
    indexStatusRepository.save(IndexStatus(currentIndex = SyncIndex.GREEN, currentIndexState = COMPLETED))
    val bookingId = 12345L
    val prisonerNumber = "O7089FD"
    prisonApi.stubGetNomsNumberForBooking(bookingId, prisonerNumber)
    prisonApi.stubOffenders(PrisonerBuilder(prisonerNumber = prisonerNumber))

    offenderSqsClient.sendMessage(
      SendMessageRequest.builder().queueUrl(offenderQueueUrl).messageBody(validOffenderBookingChangedMessage(bookingId, "OFFENDER_BOOKING-CHANGED")).build(),
    )

    await untilAsserted {
      val prisoner = prisonerRepository.get(prisonerNumber, listOf(SyncIndex.GREEN))
      assertThat(prisoner?.prisonerNumber).isEqualTo(prisonerNumber)
    }
  }

  @Test
  fun `will search for all affected prisoners when location update message received`() {
    val prisonerNumber = "O7089FD"
    indexStatusRepository.save(IndexStatus(currentIndex = SyncIndex.GREEN, currentIndexState = COMPLETED))
    prisonerRepository.switchAliasIndex(SyncIndex.GREEN)
    prisonerRepository.save(
      Prisoner().apply {
        this.prisonerNumber = prisonerNumber
        this.prisonId = "EWI"
        this.cellLocation = "RES1-2-014"
      },
      SyncIndex.GREEN,
    )
    prisonerRepository.save(
      Prisoner().apply {
        this.prisonerNumber = "O7089FF"
        this.prisonId = "EWI"
        this.cellLocation = "RES1-2-014"
      },
      SyncIndex.GREEN,
    )
    prisonerRepository.save(
      Prisoner().apply {
        this.prisonerNumber = "O7089FG"
        this.prisonId = "EWI"
        this.cellLocation = "RES1-1-014" // wrong cell
      },
      SyncIndex.GREEN,
    )
    prisonerRepository.save(
      Prisoner().apply {
        this.prisonerNumber = "O7089FH"
        this.prisonId = "MDI" // wrong prison
        this.cellLocation = "RES1-2-014"
      },
      SyncIndex.GREEN,
    )
    await untilCallTo { prisonerRepository.count(SyncIndex.GREEN) } matches { it == 4L }

    prisonApi.stubOffenders(PrisonerBuilder(prisonerNumber = prisonerNumber))

    offenderSqsClient.sendMessage(
      SendMessageRequest.builder().queueUrl(offenderQueueUrl).messageBody(validPrisonerLocationChangeMessage("AGENCY_INTERNAL_LOCATIONS-UPDATED")).build(),
    )

    await untilAsserted {
      val prisoner = prisonerRepository.get(prisonerNumber, listOf(SyncIndex.GREEN))
      assertThat(prisoner?.cellLocation).isEqualTo("A-1-1")
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
    indexStatusRepository.save(IndexStatus(currentIndex = SyncIndex.GREEN, currentIndexState = COMPLETED))
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
      val prisoner = prisonerRepository.get(prisonerNumber, listOf(SyncIndex.GREEN))
      assertThat(prisoner?.prisonerNumber).isEqualTo(prisonerNumber)
    }
  }

  @Test
  fun `will delete merge records and insert new prisoner record on booking number change`() {
    indexStatusRepository.save(IndexStatus(currentIndex = SyncIndex.GREEN, currentIndexState = COMPLETED))
    val oldPrisonerNumber = "O7089FE" // record to be removed
    prisonerRepository.save(Prisoner().also { it.prisonerNumber = oldPrisonerNumber }, SyncIndex.GREEN)
    await untilCallTo { prisonerRepository.count(SyncIndex.GREEN) } matches { it == 1L }

    val bookingId = 12345L
    val prisonerNumber = "O7089FF" // record to be inserted / updated
    prisonApi.stubGetMergedIdentifiersByBookingId(bookingId, oldPrisonerNumber)
    prisonApi.stubGetNomsNumberForBooking(bookingId, prisonerNumber)
    prisonApi.stubOffenders(PrisonerBuilder(prisonerNumber = prisonerNumber))

    offenderSqsClient.sendMessage(
      SendMessageRequest.builder().queueUrl(offenderQueueUrl).messageBody(validOffenderBookingChangedMessage(bookingId, "BOOKING_NUMBER-CHANGED")).build(),
    )

    await untilAsserted {
      assertThat(prisonerRepository.get(oldPrisonerNumber, listOf(SyncIndex.GREEN))).isNull()
      assertThat(prisonerRepository.get(prisonerNumber, listOf(SyncIndex.GREEN))?.prisonerNumber).isEqualTo(prisonerNumber)
    }
  }

  @Test
  fun `will update both indexes when rebuilding index`() {
    indexStatusRepository.save(IndexStatus(currentIndex = SyncIndex.GREEN, currentIndexState = COMPLETED, otherIndexState = BUILDING))
    val bookingId = 12346L
    val prisonerNumber = "O7089FE"
    prisonApi.stubGetNomsNumberForBooking(bookingId, prisonerNumber)
    prisonApi.stubOffenders(PrisonerBuilder(prisonerNumber = prisonerNumber))

    offenderSqsClient.sendMessage(
      SendMessageRequest.builder().queueUrl(offenderQueueUrl).messageBody(validOffenderBookingChangedMessage(bookingId, "OFFENDER_BOOKING-CHANGED")).build(),
    )

    await untilAsserted {
      assertThat(prisonerRepository.get(prisonerNumber, listOf(SyncIndex.GREEN))?.prisonerNumber).isEqualTo(prisonerNumber)
      assertThat(prisonerRepository.get(prisonerNumber, listOf(SyncIndex.BLUE))?.prisonerNumber).isEqualTo(prisonerNumber)
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
