package uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IndexState.BUILDING
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IndexState.COMPLETED
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IndexStatus
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.SyncIndex
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.PrisonerBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.wiremock.IncentivesApiExtension.Companion.incentivesApi
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.wiremock.PrisonApiExtension.Companion.prisonApi
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.wiremock.RestrictedPatientsApiExtension.Companion.restrictedPatientsApi

class DomainEventListenerIntTest : IntegrationTestBase() {
  @Test
  fun `will index a prisoner's RP data when RP message received`() {
    indexStatusRepository.save(IndexStatus(currentIndex = SyncIndex.GREEN, currentIndexState = COMPLETED))
    val prisonerNumber = "A7089FD"
    val prisoner = Prisoner().apply {
      this.prisonerNumber = prisonerNumber
      bookingId = "1234"
    }
    prisonerRepository.save(prisoner, SyncIndex.GREEN)
    prisonerRepository.save(prisoner, SyncIndex.RED)
    prisonApi.stubOffenders(PrisonerBuilder(prisonerNumber = prisonerNumber, agencyId = "OUT"))
    restrictedPatientsApi.stubGetRestrictedPatient(prisonerNumber)

    reset(prisonerSpyBeanRepository) // zero the call count

    hmppsDomainSqsClient.sendMessage(
      SendMessageRequest.builder().queueUrl(hmppsDomainQueueUrl)
        .messageBody(validRPMessage(prisonerNumber, "restricted-patients.patient.added")).build(),
    )

    await untilAsserted {
      prisonerRepository.get(prisonerNumber, listOf(SyncIndex.GREEN))!!.apply {
        assertThat(prisonerNumber).isEqualTo(prisonerNumber)
        assertThat(supportingPrisonId).isEqualTo("LEI")
      }
      prisonerRepository.get(prisonerNumber, listOf(SyncIndex.RED))!!.apply {
        assertThat(prisonerNumber).isEqualTo(prisonerNumber)
        assertThat(dischargedHospitalId).isEqualTo("HOS1")
      }
    }
    verify(prisonerSpyBeanRepository, times(1)).save(any(), eq(SyncIndex.GREEN))
    verify(prisonerSpyBeanRepository, never()).save(any(), eq(SyncIndex.BLUE))
    verify(prisonerSpyBeanRepository, never()).save(any(), eq(SyncIndex.RED))
    verify(prisonerSpyBeanRepository, times(1)).updateRestrictedPatient(
      eq(prisonerNumber),
      eq(true), eq("LEI"), eq("HOS1"), isNull(), any(), isNull(), any(), eq(SyncIndex.RED), any(),
    )
    verify(prisonerSpyBeanRepository, never()).updateIncentive(any(), any(), any(), any())
    verify(prisonerSpyBeanRepository, never()).updatePrisoner(any(), any(), any(), any())
  }

  @Test
  fun `will update old indexes when rebuilding index`() {
    indexStatusRepository.save(
      IndexStatus(
        currentIndex = SyncIndex.GREEN,
        currentIndexState = COMPLETED,
        otherIndexState = BUILDING,
      ),
    )
    val prisonerNumber = "A7089FE"
    prisonApi.stubOffenders(PrisonerBuilder(prisonerNumber = prisonerNumber))

    hmppsDomainSqsClient.sendMessage(
      SendMessageRequest.builder().queueUrl(hmppsDomainQueueUrl)
        .messageBody(validRPMessage(prisonerNumber, "restricted-patients.patient.added")).build(),
    )

    await untilAsserted {
      assertThat(prisonerRepository.get(prisonerNumber, listOf(SyncIndex.GREEN))?.prisonerNumber).isEqualTo(
        prisonerNumber,
      )
      assertThat(prisonerRepository.get(prisonerNumber, listOf(SyncIndex.BLUE))?.prisonerNumber).isEqualTo(
        prisonerNumber,
      )
      // RED index cannot be updated when the prisoner not in the index
    }
  }

  @Test
  fun `will index incentive when iep message received`() {
    indexStatusRepository.save(IndexStatus(currentIndex = SyncIndex.GREEN, currentIndexState = COMPLETED))
    val prisonerNumber = "A7089FF"
    val prisoner = Prisoner().apply {
      this.prisonerNumber = prisonerNumber
      bookingId = "1234"
    }
    prisonerRepository.save(prisoner, SyncIndex.GREEN)
    prisonerRepository.save(prisoner, SyncIndex.RED)
    prisonApi.stubOffenders(PrisonerBuilder(prisonerNumber))
    incentivesApi.stubCurrentIncentive()

    reset(prisonerSpyBeanRepository) // zero the call count

    hmppsDomainSqsClient.sendMessage(
      SendMessageRequest.builder().queueUrl(hmppsDomainQueueUrl)
        .messageBody(validIepMessage(prisonerNumber, "incentives.iep-review.inserted")).build(),
    )

    await untilAsserted {
      prisonerRepository.get(prisonerNumber, listOf(SyncIndex.GREEN))!!.apply {
        assertThat(prisonerNumber).isEqualTo(prisonerNumber)
        assertThat(currentIncentive?.level?.code).isEqualTo("STD")
      }
      prisonerRepository.get(prisonerNumber, listOf(SyncIndex.RED))!!.apply {
        assertThat(prisonerNumber).isEqualTo(prisonerNumber)
        assertThat(currentIncentive?.level?.code).isEqualTo("STD")
      }
    }
    verify(prisonerSpyBeanRepository, times(1)).save(any(), eq(SyncIndex.GREEN))
    verify(prisonerSpyBeanRepository, never()).save(any(), eq(SyncIndex.BLUE))
    verify(prisonerSpyBeanRepository, never()).save(any(), eq(SyncIndex.RED))
    verify(prisonerSpyBeanRepository, times(1)).updateIncentive(eq(prisonerNumber), any(), eq(SyncIndex.RED), any())
    verify(prisonerSpyBeanRepository, never()).updatePrisoner(any(), any(), any(), any())
    verify(prisonerSpyBeanRepository, never()).updateRestrictedPatient(
      any(), any(), any(), any(), any(),
      any(), any(), any(), any(), any(),
    )
  }
}

private fun validIepMessage(prisonerNumber: String, eventType: String) =
  """
    {
      "Type": "Notification",
      "MessageId": "20e13002-d1be-56e7-be8c-66cdd7e23341",
      "Message": "{\"eventType\":\"$eventType\", \"description\": \"some desc\", \"additionalInformation\": {\"id\":\"12345\", \"nomsNumber\":\"$prisonerNumber\"}}",
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

private fun validRPMessage(prisonerNumber: String, eventType: String) =
  """
    {
      "Type": "Notification",
      "MessageId": "20e13002-d1be-56e7-be8c-66cdd7e23341",
      "Message": "{\"eventType\":\"$eventType\", \"description\": \"some desc\", \"additionalInformation\": {\"prisonerNumber\":\"$prisonerNumber\"}}",
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
