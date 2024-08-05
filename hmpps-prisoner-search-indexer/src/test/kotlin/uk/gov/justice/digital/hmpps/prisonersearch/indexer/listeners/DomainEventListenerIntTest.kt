package uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.INDEX_STATUS_ID
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IndexState.BUILDING
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IndexState.COMPLETED
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IndexStatus
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.SyncIndex
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.PrisonerBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.wiremock.PrisonApiExtension.Companion.prisonApi

class DomainEventListenerIntTest : IntegrationTestBase() {
  @Test
  fun `will index a prisoner when iep message received`() {
    indexStatusRepository.save(IndexStatus(id = INDEX_STATUS_ID, currentIndex = SyncIndex.GREEN, currentIndexState = COMPLETED))
    val prisonerNumber = "A7089FD"
    prisonApi.stubOffenders(PrisonerBuilder(prisonerNumber = prisonerNumber))

    hmppsDomainSqsClient.sendMessage(
      SendMessageRequest.builder().queueUrl(hmppsDomainQueueUrl).messageBody(validIepMessage(prisonerNumber)).build(),
    )

    await untilAsserted {
      val prisoner = prisonerRepository.get(prisonerNumber, listOf(SyncIndex.GREEN))
      assertThat(prisoner?.prisonerNumber).isEqualTo(prisonerNumber)
    }
  }

  @Test
  fun `will update both indexes when rebuilding index`() {
    indexStatusRepository.save(IndexStatus(id = INDEX_STATUS_ID, currentIndex = SyncIndex.GREEN, currentIndexState = COMPLETED, otherIndexState = BUILDING))
    val prisonerNumber = "A7089FE"
    prisonApi.stubOffenders(PrisonerBuilder(prisonerNumber = prisonerNumber))

    hmppsDomainSqsClient.sendMessage(
      SendMessageRequest.builder().queueUrl(hmppsDomainQueueUrl).messageBody(validIepMessage(prisonerNumber)).build(),
    )

    await untilAsserted {
      assertThat(prisonerRepository.get(prisonerNumber, listOf(SyncIndex.GREEN))?.prisonerNumber).isEqualTo(prisonerNumber)
      assertThat(prisonerRepository.get(prisonerNumber, listOf(SyncIndex.BLUE))?.prisonerNumber).isEqualTo(prisonerNumber)
    }
  }

  private fun validIepMessage(prisonerNumber: String) = """
          {
            "Type": "Notification",
            "MessageId": "20e13002-d1be-56e7-be8c-66cdd7e23341",
            "Message": "{\"eventType\":\"incentives.iep-review.inserted\", \"description\": \"some desc\", \"additionalInformation\": {\"id\":\"12345\", \"nomsNumber\":\"$prisonerNumber\"}}",
            "MessageAttributes": {
              "eventType": {
                "Type": "String",
                "Value": "incentives.iep-review.updated"
              },
              "id": {
                "Type": "String",
                "Value": "cb4645f2-d0c1-4677-806a-8036ed54bf69"
              }
            }
          }
  """.trimIndent()
}
