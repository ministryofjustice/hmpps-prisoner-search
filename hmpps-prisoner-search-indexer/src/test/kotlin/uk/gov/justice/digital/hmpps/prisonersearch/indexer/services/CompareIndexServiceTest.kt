package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.opensearch.client.RestHighLevelClient
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.repository.PrisonerRepository

class CompareIndexServiceTest {

  private val indexStatusService = mock<IndexStatusService>()
  private val openSearchClient = mock<RestHighLevelClient>()
  private val telemetryClient = mock<TelemetryClient>()
  private val nomisService = mock<NomisService>()
  private val prisonerRepository = mock<PrisonerRepository>()
  private val prisonerSynchroniserService = mock<PrisonerSynchroniserService>()
  private val prisonerDifferenceService = mock<PrisonerDifferenceService>()
  private val objectMapper = ObjectMapper()

  private lateinit var compareIndexService: CompareIndexService

  @BeforeEach
  internal fun setUp() {
    compareIndexService = CompareIndexService(
      indexStatusService,
      openSearchClient,
      telemetryClient,
      nomisService,
      prisonerRepository,
      prisonerSynchroniserService,
      prisonerDifferenceService,
      objectMapper,
    )
  }

  @Nested
  @Disabled
  inner class EqualsIgnoringNulls {
    @Test
    fun `maps are equal ignoring nulls`() {
      assertThat(
        compareIndexService.equalsIgnoringNulls(
          mapOf("first" to "value", "second" to mapOf("nested" to null), "third" to null),
          mapOf("first" to "value"),
        ),
      ).isTrue()
    }

    @Test
    fun `maps are equal ignoring nulls within lists`() {
      assertThat(
        compareIndexService.equalsIgnoringNulls(
          mapOf(
            "first" to "value",
            "second" to mapOf("nested" to listOf("listitem1", mapOf("nested2" to null)), "third" to null),
          ),
          mapOf("first" to "value"),
        ),
      ).isTrue()
    }

    @Test
    fun `maps are not equal`() {
      assertThat(
        compareIndexService.equalsIgnoringNulls(
          mapOf("first" to "value1"),
          mapOf("first" to "value2"),
        ),
      ).isFalse()
    }
  }

  @Nested
  inner class DeepEquals {
    @Test
    fun `maps are equal ignoring nulls`() {
      assertThat(
        compareIndexService.deepEquals(
          mapOf(
            "prisonName" to "value",
            "dischargeDate" to null,
            "weightKilograms" to null,
            "firstName" to null,
            "youthOffender" to null,
          ),
          mapOf(
            "prisonName" to "value",
          ),
        ),
      ).isTrue()
    }

    @Test
    fun `maps are equal ignoring null lists`() {
      assertThat(
        compareIndexService.equalsIgnoringNulls(
          mapOf(
            "prisonName" to "value",
            "tattoos" to listOf(
              mapOf("bodyPart" to null),
              mapOf("comment" to null),
            ),
          ),
          mapOf("prisonName" to "value"),
        ),
      ).isTrue()
    }

    @Test
    fun `maps are equal ignoring nulls within lists`() {
      assertThat(
        compareIndexService.equalsIgnoringNulls(
          mapOf(
            "prisonName" to "value",
            "tattoos" to listOf(
              mapOf("bodyPart" to "Arm", "comment" to null),
            ),
          ),
          mapOf(
            "prisonName" to "value",
            "tattoos" to listOf(
              mapOf("bodyPart" to "Arm"),
            ),
          ),
        ),
      ).isTrue()
    }

    @Test
    fun `maps are not equal`() {
      assertThat(
        compareIndexService.deepEquals(
          mapOf("prisonName" to "value1"),
          mapOf("prisonName" to "value2"),
        ),
      ).isFalse()
    }

    @Test
    fun `maps are not equal within lists`() {
      assertThat(
        compareIndexService.equalsIgnoringNulls(
          mapOf(
            "prisonName" to "value",
            "tattoos" to listOf(
              mapOf("comment" to "comment1"),
            ),
          ),
          mapOf(
            "prisonName" to "value",
            "tattoos" to listOf(
              mapOf("comment" to "comment2"),
            ),
          ),
        ),
      ).isFalse()
    }
  }
}
