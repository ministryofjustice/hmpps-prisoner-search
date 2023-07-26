package uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners

import ch.qos.logback.classic.Level
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.exc.InvalidFormatException
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
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IndexStatus
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.SyncIndex.GREEN
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.helpers.findLogAppender
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.BuildNotInProgressException
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.PopulateIndexService
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.PrisonerPage

@JsonTest
internal class PopulateIndexListenerTest(@Autowired private val objectMapper: ObjectMapper) {
  private val populateIndexService = mock<PopulateIndexService>()

  private val listener = PopulateIndexListener(objectMapper, populateIndexService)
  private val logAppender = findLogAppender(PopulateIndexListener::class.java)

  @Nested
  inner class PopulateIndex {
    @Test
    internal fun `will call service with index name`() {
      whenever(populateIndexService.populateIndex(GREEN)).thenReturn(1)

      listener.processIndexRequest(
        """
      {
        "type": "POPULATE_INDEX",
        "index": "GREEN"
      }
        """.trimIndent(),
      )

      verify(populateIndexService).populateIndex(GREEN)
    }

    @Test
    internal fun `failed request`() {
      whenever(populateIndexService.populateIndex(GREEN)).thenThrow(BuildNotInProgressException(IndexStatus.newIndex()))

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
  inner class BadMessages {
    @Test
    internal fun `will fail for bad json`() {
      assertThatThrownBy { listener.processIndexRequest("this is bad json") }
        .isInstanceOf(JsonParseException::class.java)

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
