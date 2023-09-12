@file:Suppress("ClassName")

package uk.gov.justice.digital.hmpps.prisonersearch.indexer.resource

import com.microsoft.applicationinsights.TelemetryClient
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.times
import org.mockito.kotlin.verifyNoInteractions
import org.springframework.boot.test.mock.mockito.SpyBean
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IndexState
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IndexStatus
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.SyncIndex
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.PrisonerBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.wiremock.PrisonApiExtension.Companion.prisonApi
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue

class RefreshIndexResourceIntTest : IntegrationTestBase() {
  @SpyBean
  lateinit var telemetryClient: TelemetryClient

  @BeforeEach
  fun setUp(): Unit = prisonApi.stubOffenders(
    PrisonerBuilder("A9999AA"),
  )

  @Test
  fun `Refresh index - no differences`() {
    indexStatusRepository.save(IndexStatus(currentIndex = SyncIndex.GREEN, currentIndexState = IndexState.COMPLETED))

    webTestClient.put().uri("/refresh-index")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
      .exchange()
      .expectStatus().isAccepted

    await untilCallTo { indexSqsClient.countAllMessagesOnQueue(indexQueueUrl).get() } matches { it!! > 0 }
    await untilCallTo { indexSqsClient.countAllMessagesOnQueue(indexQueueUrl).get() } matches { it == 0 }

    verifyNoInteractions(telemetryClient)
  }

  @Test
  fun `Refresh index - unauthorised if not correct role`() {
    indexStatusRepository.save(IndexStatus(currentIndex = SyncIndex.GREEN, currentIndexState = IndexState.COMPLETED))

    webTestClient.put().uri("/refresh-index")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_VIEW")))
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `Automated reconciliation - endpoint unprotected`() {
    indexStatusRepository.save(IndexStatus(currentIndex = SyncIndex.GREEN, currentIndexState = IndexState.COMPLETED))

    webTestClient.put().uri("/refresh-index/automated")
      .exchange()
      .expectStatus().isAccepted

    await untilCallTo { indexSqsClient.countAllMessagesOnQueue(indexQueueUrl).get() } matches { it!! > 0 }
    await untilCallTo { indexSqsClient.countAllMessagesOnQueue(indexQueueUrl).get() } matches { it == 0 }
  }
}
