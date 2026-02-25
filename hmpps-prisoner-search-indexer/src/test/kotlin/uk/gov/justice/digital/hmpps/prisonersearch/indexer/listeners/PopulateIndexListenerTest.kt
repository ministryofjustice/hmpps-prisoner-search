package uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners

import ch.qos.logback.classic.Level
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.json.JsonTest
import tools.jackson.core.exc.StreamReadException
import tools.jackson.databind.exc.InvalidFormatException
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IndexStatus
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.helpers.findLogAppender
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.BuildNotInProgressException
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.PopulateIndexService
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.PrisonerPage
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.RefreshIndexService
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.RootOffenderIdPage

@JsonTest
internal class PopulateIndexListenerTest(@Autowired jsonMapper: JsonMapper) {
  private val populateIndexService = mock<PopulateIndexService>()
  private val refreshIndexService = mock<RefreshIndexService>()

  private val listener = PopulateIndexListener(jsonMapper, populateIndexService, refreshIndexService)
  private val logAppender = findLogAppender(PopulateIndexListener::class.java)

  @Nested
  inner class PopulateIndex {
    @Test
    internal fun `will call service with index name`() {
      whenever(populateIndexService.populateIndex()).thenReturn(1)

      listener.processIndexRequest(
        """
      {
        "type": "POPULATE_INDEX"
      }
        """.trimIndent(),
      )

      verify(populateIndexService).populateIndex()
    }

    @Test
    internal fun `failed request`() {
      whenever(populateIndexService.populateIndex()).thenThrow(BuildNotInProgressException(IndexStatus()))

      listener.processIndexRequest(
        """
      {
        "type": "POPULATE_INDEX"
      }
        """.trimIndent(),
      )

      assertThat(logAppender.list).anyMatch { it.message.contains("failed with error") }
    }
  }

  @Nested
  inner class RefreshIndex {
    @Test
    internal fun `will call service with index name`() {
      listener.processIndexRequest(
        """
      {
        "type": "REFRESH_INDEX"
      }
        """.trimIndent(),
      )

      verify(refreshIndexService).refreshIndex()
    }
  }

  @Nested
  inner class RefreshActiveIndex {
    @Test
    internal fun `will call service with index name`() {
      listener.processIndexRequest(
        """
      {
        "type": "REFRESH_ACTIVE_INDEX"
      }
        """.trimIndent(),
      )
      verify(refreshIndexService).refreshActiveIndex()
    }
  }

  @Nested
  inner class PopulatePrisonerPage {
    @Test
    internal fun `will call service with page details`() {
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

      verify(populateIndexService).populateIndexWithPrisonerPage(PrisonerPage(1, 1000))
    }
  }

  @Nested
  inner class RefreshPrisonerPage {
    @Test
    internal fun `will call service with page details`() {
      listener.processIndexRequest(
        """
      {
        "type": "REFRESH_PRISONER_PAGE",
        "prisonerPage": {
          "page": 1,
          "pageSize": 1000
        }
      }
        """.trimIndent(),
      )

      verify(refreshIndexService).refreshIndexWithPrisonerPage(PrisonerPage(1, 1000))
    }
  }

  @Nested
  inner class RefreshActivePrisonerPage {
    @Test
    internal fun `will call service with page details`() {
      listener.processIndexRequest(
        """
      {
        "type": "REFRESH_ACTIVE_PRISONER_PAGE",
        "rootOffenderIdPage": {
          "fromRootOffenderId": 1,
          "toRootOffenderId": 1000
        }
      }
        """.trimIndent(),
      )

      verify(refreshIndexService).refreshActiveIndexWithRootOffenderIdPage(RootOffenderIdPage(1, 1000))
    }
  }

  @Nested
  inner class PopulatePrisoner {
    @Test
    internal fun `will call service with prisoner number to populate`() {
      whenever(populateIndexService.populateIndexWithPrisoner(any())).thenReturn(Prisoner())

      listener.processIndexRequest(
        """
      {
        "type": "POPULATE_PRISONER",
        "prisonerNumber": "X12345"
      }
        """.trimIndent(),
      )

      verify(populateIndexService).populateIndexWithPrisoner("X12345")
    }
  }

  @Nested
  inner class RefreshPrisoner {
    @Test
    internal fun `will call service with prisoner number to refresh`() {
      listener.processIndexRequest(
        """
      {
        "type": "REFRESH_PRISONER",
        "prisonerNumber": "X12345"
      }
        """.trimIndent(),
      )

      verify(refreshIndexService).refreshPrisoner(prisonerNumber = "X12345")
    }
  }

  @Nested
  inner class BadMessages {
    @Test
    internal fun `will fail for bad json`() {
      assertThatThrownBy { listener.processIndexRequest("this is bad json") }
        .isInstanceOf(StreamReadException::class.java)

      assertThat(logAppender.list).anyMatch { it.message.contains("Failed to process message") && it.level == Level.ERROR }
    }

    @Test
    internal fun `will fail for unknown message type`() {
      assertThatThrownBy {
        listener.processIndexRequest(
          """
            {
              "type": "THIS_IS_AN_UNEXPECTED_MESSAGE_TYPE",
              "prisonerNumber": "X12345"
            }
          """.trimIndent(),
        )
      }.isInstanceOf(InvalidFormatException::class.java)

      assertThat(logAppender.list).anyMatch { it.message.contains("Failed to process message") && it.level == Level.ERROR }
    }
  }
}
