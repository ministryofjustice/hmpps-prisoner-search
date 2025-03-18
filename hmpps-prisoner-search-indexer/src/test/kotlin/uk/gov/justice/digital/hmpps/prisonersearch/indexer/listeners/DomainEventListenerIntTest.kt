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
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.PrisonerBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.wiremock.AlertsApiExtension.Companion.alertsApi
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.wiremock.IncentivesApiExtension.Companion.incentivesApi
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.wiremock.PrisonApiExtension.Companion.prisonApi
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.wiremock.RestrictedPatientsApiExtension.Companion.restrictedPatientsApi

class DomainEventListenerIntTest : IntegrationTestBase() {
  @Test
  fun `will index a prisoner's RP data when RP message received`() {
    val prisonerNumber = "A7089FD"
    val prisoner = Prisoner().apply {
      this.prisonerNumber = prisonerNumber
      bookingId = "1234"
    }
    prisonerRepository.save(prisoner)
    prisonApi.stubOffenders(PrisonerBuilder(prisonerNumber = prisonerNumber, agencyId = "OUT"))
    restrictedPatientsApi.stubGetRestrictedPatient(prisonerNumber)

    reset(prisonerSpyBeanRepository) // zero the call count

    hmppsDomainSqsClient.sendMessage(
      SendMessageRequest.builder().queueUrl(hmppsDomainQueueUrl)
        .messageBody(validRPMessage(prisonerNumber, "restricted-patients.patient.added")).build(),
    )

    await untilAsserted {
      prisonerRepository.get(prisonerNumber)!!.apply {
        assertThat(prisonerNumber).isEqualTo(prisonerNumber)
        assertThat(supportingPrisonId).isEqualTo("LEI")
        assertThat(dischargedHospitalId).isEqualTo("HOS1")
      }
      assertThat(getNumberOfMessagesCurrentlyOnDomainQueue()).isEqualTo(1)
    }
    verify(prisonerSpyBeanRepository, never()).save(any())
    verify(prisonerSpyBeanRepository, times(1)).updateRestrictedPatient(
      eq(prisonerNumber),
      eq(true), eq("LEI"), eq("HOS1"), isNull(), any(), isNull(), any(), any(),
    )
    verify(prisonerSpyBeanRepository, never()).updateIncentive(any(), any(), any())
    verify(prisonerSpyBeanRepository, never()).updatePrisoner(any(), any(), any())
  }

  @Test
  fun `will index incentive when iep message received`() {
    val prisonerNumber = "A7089FF"
    val prisoner = Prisoner().apply {
      this.prisonerNumber = prisonerNumber
      bookingId = "1234"
    }
    prisonerRepository.save(prisoner)
    prisonApi.stubOffenders(PrisonerBuilder(prisonerNumber))
    incentivesApi.stubCurrentIncentive()

    reset(prisonerSpyBeanRepository) // zero the call count

    hmppsDomainSqsClient.sendMessage(
      SendMessageRequest.builder().queueUrl(hmppsDomainQueueUrl)
        .messageBody(validIepMessage(prisonerNumber, "incentives.iep-review.inserted")).build(),
    )

    await untilAsserted {
      prisonerRepository.get(prisonerNumber)!!.apply {
        assertThat(prisonerNumber).isEqualTo(prisonerNumber)
        assertThat(currentIncentive?.level?.code).isEqualTo("STD")
      }
      assertThat(getNumberOfMessagesCurrentlyOnDomainQueue()).isEqualTo(1)
    }
    verify(prisonerSpyBeanRepository, never()).save(any())
    verify(prisonerSpyBeanRepository, times(1)).updateIncentive(eq(prisonerNumber), any(), any())
    verify(prisonerSpyBeanRepository, never()).updatePrisoner(any(), any(), any())
    verify(prisonerSpyBeanRepository, never()).updateRestrictedPatient(
      any(), any(), any(), any(), any(),
      any(), any(), any(), any(),
    )
  }

  @Test
  fun `will index alerts when alerts message received`() {
    val prisonerNumber = "A7089FF"
    val prisoner = Prisoner().apply {
      this.prisonerNumber = prisonerNumber
      bookingId = "1234"
    }
    prisonerRepository.save(prisoner)
    alertsApi.stubSuccess()

    reset(prisonerSpyBeanRepository) // zero the call count

    hmppsDomainSqsClient.sendMessage(
      SendMessageRequest.builder().queueUrl(hmppsDomainQueueUrl)
        .messageBody(validAlertsMessage(prisonerNumber, "person.alert.created")).build(),
    )

    await untilAsserted {
      prisonerRepository.get(prisonerNumber)!!.apply {
        assertThat(prisonerNumber).isEqualTo(prisonerNumber)
        val alert = alerts?.first()
        assertThat(alert?.alertCode).isEqualTo("ABC")
        assertThat(alert?.alertType).isEqualTo("A")
        assertThat(alert?.active).isTrue()
        assertThat(alert?.expired).isTrue()
      }
      assertThat(getNumberOfMessagesCurrentlyOnDomainQueue()).isEqualTo(1)
    }
    verify(prisonerSpyBeanRepository, never()).save(any())
    verify(prisonerSpyBeanRepository, times(1)).updateAlerts(eq(prisonerNumber), any(), any())
    verify(prisonerSpyBeanRepository, never()).updatePrisoner(any(), any(), any())
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

private fun validAlertsMessage(prisonerNumber: String, eventType: String) =
  """
  {
    "Type" : "Notification",
    "MessageId" : "55569f56-8858-5ef8-aeab-ff05da86cf1e",
    "TopicArn" : "arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-97e6567cf80881a8a52290ff2c269b08",
    "Message" : "{\"eventType\":\"$eventType\",\"additionalInformation\":{\"alertUuid\":\"01953611-1bf7-7f1c-8b81-3e16d98acb14\",\"alertCode\":\"HA\",\"source\":\"DPS\"},\"version\":1,\"description\":\"An alert has been updated in the alerts service\",\"occurredAt\":\"2025-03-18T10:53:18.091892898Z\",\"detailUrl\":\"https://alerts-api.hmpps.service.justice.gov.uk/alerts/01953611-1bf7-7f1c-8b81-3e16d98acb14\",\"personReference\":{\"identifiers\":[{\"type\":\"NOMS\",\"value\":\"$prisonerNumber\"}]}}",
    "Timestamp" : "2025-03-18T10:53:18.130Z",
    "SignatureVersion" : "1",
    "Signature" : "cqRwyU35w91AyxCnWCE5NXQf49vJON6HKPysfOc9CAsqGwcS6T/UmAsj2FJt1SI48+7L/yg29SbBoizM1hZ+9OtB56SC+OfLE0vMoswIN2BLLwas166VQ4L2uSsxuvrF1SRONmFulvxm/9qWOpu9Zb0LR3C41HSK8EN/JDWUuq69qAhY6tqXmJe/DiDx1ovTu7WOBxNFWvL3kxChz/iYO7m5TFewiieLkA1V6PmZRzDSljMhxpTdxj7Lt4hVTF8j/uVxdvQeoSBysSDTDjvqHFONEftoH/Lpn/7tWxfsbyArqLa/SCODoDt6dpcdiEGaHbZYBTAkSNM3Fg8y7uApJA==",
    "SigningCertURL" : "https://sns.eu-west-2.amazonaws.com/SimpleNotificationService-9c6465fa7f48f5cacd23014631ec1136.pem",
    "UnsubscribeURL" : "https://sns.eu-west-2.amazonaws.com/?Action=Unsubscribe&SubscriptionArn=arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-97e6567cf80881a8a52290ff2c269b08:340b799a-084f-4027-a214-510087556d97",
    "MessageAttributes" : {
      "noTracing" : {"Type":"String","Value":"true"},
      "eventType" : {"Type":"String","Value":"$eventType"}
    }
  }
  """.trimIndent()
