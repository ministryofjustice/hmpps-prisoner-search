package uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners

import ch.qos.logback.classic.Level
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.json.JsonTest
import tools.jackson.core.exc.StreamReadException
import tools.jackson.databind.exc.InvalidFormatException
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.helpers.findLogAppender
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.RefreshIndexService
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.RootOffenderIdPage

@JsonTest
class PopulateIndexListenerTest(@Autowired jsonMapper: JsonMapper) {
  private val refreshIndexService = mock<RefreshIndexService>()

  private val listener = PopulateIndexListener(jsonMapper, refreshIndexService)
  private val logAppender = findLogAppender(PopulateIndexListener::class.java)

  @Nested
  inner class RefreshIndex {
    @Test
    fun `will call service with index name`() {
      listener.processIndexRequest(
        """
      {
        "type": "REFRESH_INDEX",
        "domainEvents": true
      }
        """.trimIndent(),
      )

      verify(refreshIndexService).refreshIndex(true)
    }
  }

  @Nested
  inner class RefreshActiveIndex {
    @Test
    fun `will call service with index name`() {
      listener.processIndexRequest(
        """
      {
        "type": "REFRESH_ACTIVE_INDEX"
      }
        """.trimIndent(),
      )
      verify(refreshIndexService).refreshActiveIndex(false)
    }
  }

  @Nested
  inner class RefreshPrisonerPage {
    @Test
    fun `will call service with page details`() {
      listener.processIndexRequest(
        """
      {
        "type": "REFRESH_PRISONER_PAGE",
        "rootOffenderIdPage": {
          "fromRootOffenderId": 1,
          "toRootOffenderId": 1000
        }
      }
        """.trimIndent(),
      )

      verify(refreshIndexService).refreshIndexWithRootOffenderIdPage(RootOffenderIdPage(1, 1000), false)
    }
  }

  @Nested
  inner class RefreshActivePrisonerPage {
    @Test
    fun `will call service with page details`() {
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

      verify(refreshIndexService).refreshActiveIndexWithRootOffenderIdPage(RootOffenderIdPage(1, 1000), false)
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

      verify(refreshIndexService).refreshPrisoner(prisonerNumber = "X12345", domainEvents = false)
    }
  }

  @Nested
  inner class BadMessages {
    @Test
    fun `will fail for bad json`() {
      assertThatThrownBy { listener.processIndexRequest("this is bad json") }
        .isInstanceOf(StreamReadException::class.java)

      assertThat(logAppender.list).anyMatch { it.message.contains("Failed to process message") && it.level == Level.ERROR }
    }

    @Test
    fun `will fail for unknown message type`() {
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
