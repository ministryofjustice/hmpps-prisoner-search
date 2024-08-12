package uk.gov.justice.digital.hmpps.prisonersearch.indexer.repository

import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.fail
import org.opensearch.action.admin.indices.alias.get.GetAliasesRequest
import org.opensearch.action.get.GetRequest
import org.opensearch.client.RequestOptions
import org.opensearch.client.RestHighLevelClient
import org.opensearch.client.indices.CreateIndexRequest
import org.opensearch.client.indices.GetIndexRequest
import org.opensearch.client.indices.GetMappingsResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.OptimisticLockingFailureException
import uk.gov.justice.digital.hmpps.prisonersearch.common.config.OpenSearchIndexConfiguration.Companion.PRISONER_INDEX
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.CurrentIncentive
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IncentiveLevel
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.SyncIndex.BLUE
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.SyncIndex.GREEN
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.IntegrationTestBase
import java.time.LocalDate
import java.time.LocalDateTime

internal class PrisonerRepositoryTest : IntegrationTestBase() {

  @Autowired
  lateinit var highLevelClient: RestHighLevelClient

  @Nested
  inner class CreateIndex {
    @Nested
    inner class NoIndexExists {
      @BeforeEach
      fun setUp() {
        deletePrisonerIndices()
      }

      @Test
      fun `will create a brand new index`() {
        prisonerRepository.createIndex(BLUE)

        assertThat(highLevelClient.indices().exists(GetIndexRequest(BLUE.indexName), RequestOptions.DEFAULT)).isTrue()
        assertThat(highLevelClient.indices().exists(GetIndexRequest(GREEN.indexName), RequestOptions.DEFAULT)).isFalse()
      }

      @Test
      fun `can create either index`() {
        prisonerRepository.createIndex(GREEN)

        assertThat(highLevelClient.indices().exists(GetIndexRequest(BLUE.indexName), RequestOptions.DEFAULT)).isFalse()
        assertThat(highLevelClient.indices().exists(GetIndexRequest(GREEN.indexName), RequestOptions.DEFAULT)).isTrue()
      }
    }

    @Nested
    inner class OneIndexExists {
      @BeforeEach
      fun setUp() {
        deletePrisonerIndices()
        highLevelClient.safeIndexCreate(GREEN.indexName)
      }

      @Test
      fun `will create a brand new index`() {
        prisonerRepository.createIndex(BLUE)

        assertThat(highLevelClient.indices().exists(GetIndexRequest(BLUE.indexName), RequestOptions.DEFAULT)).isTrue()
        assertThat(highLevelClient.indices().exists(GetIndexRequest(GREEN.indexName), RequestOptions.DEFAULT)).isTrue()
      }

      @Test
      fun `cannot create an index that already exists`() {
        assertThatThrownBy { prisonerRepository.createIndex(GREEN) }.hasMessageContaining("already exists")
      }
    }
  }

  @Nested
  inner class Count {
    @Test
    fun `will count the number of prisoners in an empty index`() {
      assertThat(prisonerRepository.count(BLUE)).isEqualTo(0)
      assertThat(prisonerRepository.count(GREEN)).isEqualTo(0)
    }

    @Test
    fun `will count the prisoners`() {
      prisonerRepository.save(Prisoner().also { it.prisonerNumber = "X12341" }, BLUE)
      prisonerRepository.save(Prisoner().also { it.prisonerNumber = "X12342" }, BLUE)
      prisonerRepository.save(Prisoner().also { it.prisonerNumber = "X12343" }, BLUE)
      prisonerRepository.save(Prisoner().also { it.prisonerNumber = "X12344" }, BLUE)

      await untilCallTo { prisonerRepository.count(BLUE) } matches { it == 4L }
      assertThat(prisonerRepository.count(GREEN)).isEqualTo(0L)
    }

    @Test
    fun `will return -1 if index doesn't exist`() {
      assertThat(prisonerRepository.count(BLUE)).isEqualTo(0L)
      prisonerRepository.deleteIndex(BLUE)
      assertThat(prisonerRepository.count(BLUE)).isEqualTo(-1L)
    }
  }

  @Nested
  inner class Get {

    @Test
    fun `will get a prisoner in the correct index`() {
      prisonerRepository.save(
        Prisoner().apply { prisonerNumber = "X12345" },
        BLUE,
      )

      assertThat(prisonerRepository.get("X12345", listOf(BLUE))).extracting("prisonerNumber").isEqualTo("X12345")
      assertThat(prisonerRepository.get("X12345", listOf(GREEN))).isNull()
    }

    @Test
    fun `will check all supplied indices to find prisoner`() {
      prisonerRepository.save(
        Prisoner().apply { prisonerNumber = "X12345" },
        BLUE,
      )

      assertThat(prisonerRepository.get("X12345", listOf(GREEN, BLUE))).extracting("prisonerNumber").isEqualTo("X12345")
    }

    @Test
    fun `will get prisoner`() {
      prisonerRepository.save(
        Prisoner().apply {
          prisonerNumber = "ABC123D"
          croNumber = "X12345"
          shoeSize = 12
        },
        BLUE,
      )

      val prisoner = prisonerRepository.get("ABC123D", listOf(BLUE))
      assertThat(prisoner).isNotNull

      assertThat(prisoner!!.shoeSize).isEqualTo(12)
      assertThat(prisoner.croNumber).isEqualTo("X12345")
      assertThat(prisoner.prisonerNumber).isEqualTo("ABC123D")
    }
  }

  @Nested
  inner class Save {

    @Test
    fun `will save prisoner in the correct index`() {
      prisonerRepository.save(
        Prisoner().apply { prisonerNumber = "X12345" },
        BLUE,
      )

      assertThat(highLevelClient.get(GetRequest(BLUE.indexName).id("X12345"), RequestOptions.DEFAULT).isExists).isTrue()
      assertThat(
        highLevelClient.get(
          GetRequest(GREEN.indexName).id("X12345"),
          RequestOptions.DEFAULT,
        ).isExists,
      ).isFalse()
    }

    @Test
    fun `will save json`() {
      prisonerRepository.save(
        Prisoner().apply {
          prisonerNumber = "ABC123D"
          croNumber = "X12345"
          shoeSize = 12
        },
        BLUE,
      )

      val json = highLevelClient.get(GetRequest(BLUE.indexName).id("ABC123D"), RequestOptions.DEFAULT).sourceAsString

      assertThatJson(json).node("shoeSize").isEqualTo(12)
      assertThatJson(json).node("croNumber").isEqualTo("X12345")
      val prisonerDetail = gson.fromJson(json, Prisoner::class.java)
      assertThat(prisonerDetail.prisonerNumber).isEqualTo("ABC123D")
    }
  }

  @Nested
  inner class GetSummary {
    @Test
    fun `prisoner does not exist`() {
      prisonerRepository.save(Prisoner().apply { prisonerNumber = "X12345" }, BLUE)

      assertThat(prisonerRepository.getSummary("nonexistent", listOf(BLUE))).isNull()
      assertThat(prisonerRepository.getSummary("X12345", listOf(GREEN))).isNull()
    }

    @Test
    fun `prisoner exists but has no bookings`() {
      prisonerRepository.save(
        Prisoner().apply {
          prisonerNumber = "X12345"
        },
        BLUE,
      )

      assertThat(prisonerRepository.getSummary("X12345", listOf(BLUE))?.prisonerNumber).isEqualTo("X12345")
      assertThat(prisonerRepository.getSummary("X12345", listOf(BLUE))?.bookingId).isNull()
    }

    @Test
    fun `prisoner exists but with no incentive data`() {
      prisonerRepository.save(
        Prisoner().apply {
          prisonerNumber = "X12345"
          bookingId = "1234"
        },
        BLUE,
      )

      assertThat(prisonerRepository.getSummary("X12345", listOf(BLUE))?.prisonerNumber).isEqualTo("X12345")
      assertThat(prisonerRepository.getSummary("X12345", listOf(BLUE))?.bookingId).isEqualTo(1234)
    }

    @Test
    fun `will get incentive data`() {
      val testIncentive = CurrentIncentive(
        IncentiveLevel("code2", "description2"),
        LocalDateTime.parse("2024-08-14T15:16:17"),
        LocalDate.parse("2024-11-27"),
      )
      prisonerRepository.save(
        Prisoner().apply {
          prisonerNumber = "X12345"
          currentIncentive = testIncentive
        },
        BLUE,
      )

      assertThat(prisonerRepository.getSummary("X12345", listOf(BLUE))!!.currentIncentive).isEqualTo(testIncentive)
    }
  }

  @Nested
  inner class UpdateIncentive {

    @Test
    fun `will update prisoner with new incentive data`() {
      prisonerRepository.save(Prisoner().apply { prisonerNumber = "X12345" }, BLUE)

      prisonerRepository.updateIncentive(
        "X12345",
        CurrentIncentive(
          IncentiveLevel("code2", "description2"),
          LocalDateTime.parse("2024-08-14T15:16:17"),
          LocalDate.parse("2024-11-27"),
        ),
        BLUE,
        prisonerRepository.getSummary("X12345", listOf(BLUE))!!,
      )
      val data = prisonerRepository.get("X12345", listOf(BLUE))?.currentIncentive!!
      assertThat(data.level.code).isEqualTo("code2")
      assertThat(data.level.description).isEqualTo("description2")
      assertThat(data.dateTime).isEqualTo(LocalDateTime.parse("2024-08-14T15:16:17"))
      assertThat(data.nextReviewDate).isEqualTo(LocalDate.parse("2024-11-27"))
    }

    @Test
    fun `will update prisoner with existing incentive data`() {
      prisonerRepository.save(
        Prisoner().apply {
          prisonerNumber = "X12345"
          currentIncentive = CurrentIncentive(
            IncentiveLevel("code1", "description1"),
            LocalDateTime.parse("2023-07-13T14:15:16"),
            LocalDate.parse("2023-08-17"),
          )
        },
        BLUE,
      )

      prisonerRepository.updateIncentive(
        "X12345",
        CurrentIncentive(
          IncentiveLevel("code2", "description2"),
          LocalDateTime.parse("2024-08-14T15:16:17"),
          LocalDate.parse("2024-11-27"),
        ),
        BLUE,
        prisonerRepository.getSummary("X12345", listOf(BLUE))!!,
      )
      val data = prisonerRepository.get("X12345", listOf(BLUE))?.currentIncentive!!
      assertThat(data.level.code).isEqualTo("code2")
      assertThat(data.level.description).isEqualTo("description2")
      assertThat(data.dateTime).isEqualTo(LocalDateTime.parse("2024-08-14T15:16:17"))
      assertThat(data.nextReviewDate).isEqualTo(LocalDate.parse("2024-11-27"))
    }

    @Test
    fun `will update prisoner with no incentive data`() {
      prisonerRepository.save(
        Prisoner().apply {
          prisonerNumber = "X12345"
          currentIncentive = CurrentIncentive(
            IncentiveLevel("code1", "description1"),
            LocalDateTime.parse("2023-07-13T14:15:16"),
            LocalDate.parse("2023-08-17"),
          )
        },
        BLUE,
      )

      prisonerRepository.updateIncentive(
        "X12345",
        null,
        BLUE,
        prisonerRepository.getSummary("X12345", listOf(BLUE))!!,
      )
      assertThat(prisonerRepository.get("X12345", listOf(BLUE))!!.currentIncentive).isNull()
    }

    @Test
    fun `wrong sequence + 1 throws exception`() {
      prisonerRepository.save(
        Prisoner().apply {
          prisonerNumber = "X12345"
          currentIncentive = CurrentIncentive(
            IncentiveLevel("code1", "description1"),
            LocalDateTime.parse("2023-07-13T14:15:16"),
            LocalDate.parse("2023-08-17"),
          )
        },
        BLUE,
      )

      val summary = prisonerRepository.getSummary("X12345", listOf(BLUE))!!
      assertThrows<OptimisticLockingFailureException> {
        prisonerRepository.updateIncentive(
          "X12345",
          CurrentIncentive(
            IncentiveLevel("code2", "description2"),
            LocalDateTime.parse("2024-08-14T15:16:17"),
            LocalDate.parse("2024-11-27"),
          ),
          BLUE,
          summary.copy(sequenceNumber = summary.sequenceNumber + 1),
        )
      }
    }

    @Test
    fun `wrong primaryTerm + 1 throws exception`() {
      prisonerRepository.save(
        Prisoner().apply {
          prisonerNumber = "X12345"
          currentIncentive = CurrentIncentive(
            IncentiveLevel("code1", "description1"),
            LocalDateTime.parse("2023-07-13T14:15:16"),
            LocalDate.parse("2023-08-17"),
          )
        },
        BLUE,
      )

      val summary = prisonerRepository.getSummary("X12345", listOf(BLUE))!!
      assertThrows<OptimisticLockingFailureException> {
        prisonerRepository.updateIncentive(
          "X12345",
          CurrentIncentive(
            IncentiveLevel("code2", "description2"),
            LocalDateTime.parse("2024-08-14T15:16:17"),
            LocalDate.parse("2024-11-27"),
          ),
          BLUE,
          summary.copy(primaryTerm = summary.primaryTerm + 1),
        )
      }
    }
  }

  @Nested
  inner class Delete {

    @Test
    fun `will delete prisoner`() {
      prisonerRepository.save(
        Prisoner().apply { prisonerNumber = "X12345" },
        BLUE,
      )
      prisonerRepository.save(
        Prisoner().apply { prisonerNumber = "X12345" },
        GREEN,
      )
      assertThat(highLevelClient.get(GetRequest(BLUE.indexName).id("X12345"), RequestOptions.DEFAULT).isExists).isTrue()

      prisonerRepository.delete(prisonerNumber = "X12345")

      assertThat(
        highLevelClient.get(GetRequest(BLUE.indexName).id("X12345"), RequestOptions.DEFAULT).isExists,
      ).isFalse()
      assertThat(
        highLevelClient.get(GetRequest(GREEN.indexName).id("X12345"), RequestOptions.DEFAULT).isExists,
      ).isFalse()
    }
  }

  @Nested
  inner class DeleteIndex {
    @BeforeEach
    fun setUp() {
      deletePrisonerIndices()
    }

    @Test
    fun `will delete an existing index`() {
      prisonerRepository.createIndex(BLUE)

      assertThat(highLevelClient.indices().exists(GetIndexRequest(BLUE.indexName), RequestOptions.DEFAULT)).isTrue()
      assertThat(highLevelClient.indices().exists(GetIndexRequest(GREEN.indexName), RequestOptions.DEFAULT)).isFalse()

      prisonerRepository.deleteIndex(BLUE)

      assertThat(highLevelClient.indices().exists(GetIndexRequest(BLUE.indexName), RequestOptions.DEFAULT)).isFalse()
      assertThat(highLevelClient.indices().exists(GetIndexRequest(GREEN.indexName), RequestOptions.DEFAULT)).isFalse()
    }

    @Test
    fun `will leave the other index alone`() {
      prisonerRepository.createIndex(BLUE)
      prisonerRepository.createIndex(GREEN)

      assertThat(highLevelClient.indices().exists(GetIndexRequest(BLUE.indexName), RequestOptions.DEFAULT)).isTrue()
      assertThat(highLevelClient.indices().exists(GetIndexRequest(GREEN.indexName), RequestOptions.DEFAULT)).isTrue()

      prisonerRepository.deleteIndex(BLUE)

      assertThat(highLevelClient.indices().exists(GetIndexRequest(BLUE.indexName), RequestOptions.DEFAULT)).isFalse()
      assertThat(highLevelClient.indices().exists(GetIndexRequest(GREEN.indexName), RequestOptions.DEFAULT)).isTrue()
    }

    @Test
    fun `will not complain if index to delete does not exist`() {
      assertThat(highLevelClient.indices().exists(GetIndexRequest(BLUE.indexName), RequestOptions.DEFAULT)).isFalse()

      prisonerRepository.deleteIndex(BLUE)

      assertThat(highLevelClient.indices().exists(GetIndexRequest(BLUE.indexName), RequestOptions.DEFAULT)).isFalse()
    }
  }

  @Nested
  inner class DoesIndexExist {
    @BeforeEach
    fun setUp() {
      deletePrisonerIndices()
    }

    @Test
    fun `will report true when index exists`() {
      prisonerRepository.createIndex(BLUE)
      assertThat(prisonerRepository.doesIndexExist(BLUE)).isTrue()
      assertThat(prisonerRepository.doesIndexExist(GREEN)).isFalse()
    }

    @Test
    fun `will report false when index does not exists`() {
      assertThat(prisonerRepository.doesIndexExist(BLUE)).isFalse()
      assertThat(prisonerRepository.doesIndexExist(GREEN)).isFalse()
    }
  }

  @Nested
  inner class SwitchAliasIndex {

    @Nested
    inner class BeforeAliasExists {
      @Test
      fun `can create an alias for active index`() {
        prisonerRepository.switchAliasIndex(GREEN)
        assertThat(highLevelClient.indices().exists(GetIndexRequest(PRISONER_INDEX), RequestOptions.DEFAULT)).isTrue()
        assertThat(
          highLevelClient.indices()
            .getAlias(GetAliasesRequest().aliases(PRISONER_INDEX), RequestOptions.DEFAULT).aliases,
        ).containsKey(GREEN.indexName)
        assertThat(
          highLevelClient.indices()
            .getAlias(GetAliasesRequest().aliases(PRISONER_INDEX), RequestOptions.DEFAULT).aliases,
        ).doesNotContainKey(BLUE.indexName)
      }

      @Test
      fun `alias is pointing at no index`() {
        val indexes = prisonerRepository.prisonerAliasIsPointingAt()

        assertThat(indexes).isEmpty()
      }
    }

    @Nested
    inner class WhenAliasExists {
      @BeforeEach
      fun setUp() {
        prisonerRepository.switchAliasIndex(GREEN)
      }

      @Test
      fun `can switch an alias for active index`() {
        prisonerRepository.switchAliasIndex(BLUE)
        assertThat(highLevelClient.indices().exists(GetIndexRequest(PRISONER_INDEX), RequestOptions.DEFAULT)).isTrue()
        assertThat(
          highLevelClient.indices()
            .getAlias(GetAliasesRequest().aliases(PRISONER_INDEX), RequestOptions.DEFAULT).aliases,
        ).containsKey(BLUE.indexName)
        assertThat(
          highLevelClient.indices()
            .getAlias(GetAliasesRequest().aliases(PRISONER_INDEX), RequestOptions.DEFAULT).aliases,
        ).doesNotContainKey(GREEN.indexName)
      }

      @Test
      fun `alias is pointing at active index`() {
        prisonerRepository.switchAliasIndex(BLUE)
        val indexes = prisonerRepository.prisonerAliasIsPointingAt()

        assertThat(indexes).containsExactly(BLUE.indexName)
      }
    }

    @Nested
    inner class WhenAliasExistsOnCorrectIndex {
      @BeforeEach
      fun setUp() {
        prisonerRepository.switchAliasIndex(BLUE)
      }

      @Test
      fun `will keep an alias for active index`() {
        prisonerRepository.switchAliasIndex(BLUE)
        assertThat(highLevelClient.indices().exists(GetIndexRequest(PRISONER_INDEX), RequestOptions.DEFAULT)).isTrue()
        assertThat(
          highLevelClient.indices()
            .getAlias(GetAliasesRequest().aliases(PRISONER_INDEX), RequestOptions.DEFAULT).aliases,
        ).containsKey(BLUE.indexName)
        assertThat(
          highLevelClient.indices()
            .getAlias(GetAliasesRequest().aliases(PRISONER_INDEX), RequestOptions.DEFAULT).aliases,
        ).doesNotContainKey(GREEN.indexName)
      }
    }
  }
}

fun RestHighLevelClient.safeIndexCreate(name: String) {
  if (this.indices().exists(GetIndexRequest(name), RequestOptions.DEFAULT).not()) {
    this.indices().create(CreateIndexRequest(name), RequestOptions.DEFAULT)
  }
}

fun GetMappingsResponse.properties(index: String): Map<String, Any> {
  val mapping = this.mappings()[index]?.sourceAsMap()!!
  val mappingProperties = mapping["properties"] as Map<*, *>
  @Suppress("UNCHECKED_CAST")
  return mappingProperties as Map<String, Any>
}

fun Map<*, *>.value(property: String): String {
  val properties = property.split(".")
  var currentMap = this
  properties.forEach {
    when (val currentValue = currentMap[it]) {
      is Map<*, *> -> currentMap = currentValue
      is String -> return currentValue
    }
  }
  fail("$property not found")
}
