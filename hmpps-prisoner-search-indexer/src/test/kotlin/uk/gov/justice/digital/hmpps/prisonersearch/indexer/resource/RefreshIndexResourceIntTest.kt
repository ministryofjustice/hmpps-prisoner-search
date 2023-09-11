@file:Suppress("ClassName")

package uk.gov.justice.digital.hmpps.prisonersearch.indexer.resource

import org.junit.jupiter.api.Test
import org.mockito.kotlin.times
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IndexState
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IndexStatus
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.SyncIndex
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.IntegrationTestBase

class RefreshIndexResourceIntTest : IntegrationTestBase() {

  @Test
  fun `Refresh index - no differences`() {
    indexStatusRepository.save(IndexStatus(currentIndex = SyncIndex.GREEN, currentIndexState = IndexState.COMPLETED))

    webTestClient.put().uri("/refresh-index")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
      .exchange()
      .expectStatus().isAccepted

//    await untilCallTo { indexSqsClient.countAllMessagesOnQueue(indexQueueUrl).get() } matches { it!! > 0 }
//    await untilCallTo { indexSqsClient.countAllMessagesOnQueue(indexQueueUrl).get() } matches { it == 0 }
  }

  @Test
  fun `Refresh index - unauthorised if not correct role`() {
    indexStatusRepository.save(IndexStatus(currentIndex = SyncIndex.GREEN, currentIndexState = IndexState.COMPLETED))

    webTestClient.put().uri("/refresh-index")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_VIEW")))
      .exchange()
      .expectStatus().isForbidden

//    await untilCallTo { indexSqsClient.countAllMessagesOnQueue(indexQueueUrl).get() } matches { it!! > 0 }
//    await untilCallTo { indexSqsClient.countAllMessagesOnQueue(indexQueueUrl).get() } matches { it == 0 }
  }

  @Test
  fun `Automated reconciliation - endpoint unprotected`() {
    indexStatusRepository.save(IndexStatus(currentIndex = SyncIndex.GREEN, currentIndexState = IndexState.COMPLETED))

    webTestClient.put().uri("/refresh-index/automated")
      .exchange()
      .expectStatus().isAccepted

//    await untilCallTo { indexSqsClient.countAllMessagesOnQueue(indexQueueUrl).get() } matches { it!! > 0 }
//    await untilCallTo { indexSqsClient.countAllMessagesOnQueue(indexQueueUrl).get() } matches { it == 0 }
  }
}
