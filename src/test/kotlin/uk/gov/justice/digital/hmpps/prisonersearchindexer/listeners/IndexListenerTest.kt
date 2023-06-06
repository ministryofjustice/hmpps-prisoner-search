package uk.gov.justice.digital.hmpps.prisonersearchindexer.listeners

import arrow.core.left
import arrow.core.right
import ch.qos.logback.classic.Level
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonersearchindexer.helpers.findLogAppender
import uk.gov.justice.digital.hmpps.prisonersearchindexer.model.IndexStatus
import uk.gov.justice.digital.hmpps.prisonersearchindexer.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearchindexer.model.SyncIndex.GREEN
import uk.gov.justice.digital.hmpps.prisonersearchindexer.services.BuildNotInProgressError
import uk.gov.justice.digital.hmpps.prisonersearchindexer.services.IndexService
import uk.gov.justice.digital.hmpps.prisonersearchindexer.services.PrisonerPage

internal class IndexListenerTest {
  private val indexService = mock<IndexService>()
  private val listener = IndexListener(Gson(), indexService)

  @Nested
  inner class PopulateIndex {
    @Test
    internal fun `will call service with index name`() {
      whenever(indexService.populateIndex(GREEN)).thenReturn(1.right())

      listener.processIndexRequest(
        """
      {
        "type": "POPULATE_INDEX",
        "index": "GREEN"
      }
        """.trimIndent(),
      )

      verify(indexService).populateIndex(GREEN)
    }

    @Test
    internal fun `failed request`() {
      val logAppender = findLogAppender(IndexListener::class.java)
      whenever(indexService.populateIndex(GREEN)).thenReturn(BuildNotInProgressError(IndexStatus.newIndex()).left())

      listener.processIndexRequest(
        """
      {
        "type": "POPULATE_INDEX",
        "index": "GREEN"
      }
        """.trimIndent(),
      )

      assertThat(logAppender.list).anyMatch { it.message.contains("failed with error") }
    }
  }

  @Nested
  inner class PopulatePrisonerPage {
    @Test
    internal fun `will call service with page details`() {
      whenever(indexService.populateIndexWithPrisonerPage(any())).thenReturn(Unit.right())

      listener.processIndexRequest(
        """
      {
        "type": "POPULATE_PRISONER_PAGE",
        "prisonerPage": {
          "page": 1,
          "pageSize": 1000
        }
      }
        """.trimIndent(),
      )

      verify(indexService).populateIndexWithPrisonerPage(PrisonerPage(1, 1000))
    }
  }

  @Nested
  inner class PopulatePrisoner {
    @Test
    internal fun `will call service with prisoner number to populate`() {
      whenever(indexService.populateIndexWithPrisoner(any())).thenReturn(Prisoner().right())

      listener.processIndexRequest(
        """
      {
        "type": "POPULATE_PRISONER",
        "prisonerNumber": "X12345"
      }
        """.trimIndent(),
      )

      verify(indexService).populateIndexWithPrisoner("X12345")
    }
  }

  @Nested
  inner class BadMessages {
    @Test
    internal fun `will fail for bad json`() {
      val logAppender = findLogAppender(IndexListener::class.java)

      assertThatThrownBy { listener.processIndexRequest("this is bad json") }
        .isInstanceOf(JsonSyntaxException::class.java)

      assertThat(logAppender.list).anyMatch { it.message.contains("Failed to process message") && it.level == Level.ERROR }
    }

    @Test
    internal fun `will fail for unknown message type`() {
      val logAppender = findLogAppender(IndexListener::class.java)

      assertThatThrownBy {
        listener.processIndexRequest(
          """
            {
              "type": "THIS_IS_AN_UNEXPECTED_MESSAGE_TYPE",
              "prisonerNumber": "X12345"
            }
          """.trimIndent(),
        )
      }.isInstanceOf(IllegalArgumentException::class.java)

      assertThat(logAppender.list).anyMatch { it.message.contains("Unknown request type for message") && it.level == Level.ERROR }
    }
  }
}
