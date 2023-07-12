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
import org.junit.jupiter.api.fail
import org.opensearch.action.admin.indices.alias.get.GetAliasesRequest
import org.opensearch.action.get.GetRequest
import org.opensearch.client.RequestOptions
import org.opensearch.client.RestHighLevelClient
import org.opensearch.client.indices.CreateIndexRequest
import org.opensearch.client.indices.GetIndexRequest
import org.opensearch.client.indices.GetMappingsResponse
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonersearch.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.model.SyncIndex.BLUE
import uk.gov.justice.digital.hmpps.prisonersearch.model.SyncIndex.GREEN

internal class PrisonerRepositoryTest : IntegrationTestBase() {

  @Autowired
  lateinit var highLevelClient: RestHighLevelClient

  @Autowired
  lateinit var prisonerRepository: PrisonerRepository

  @Nested
  inner class CreateIndex {
    @Nested
    inner class NoIndexExists {
      @BeforeEach
      internal fun setUp() {
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
      internal fun setUp() {
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
    internal fun `will count the number of prisoners in an empty index`() {
      assertThat(prisonerRepository.count(BLUE)).isEqualTo(0)
      assertThat(prisonerRepository.count(GREEN)).isEqualTo(0)
    }

    @Test
    internal fun `will count the prisoners`() {
      prisonerRepository.save(Prisoner().also { it.prisonerNumber = "X12341" }, BLUE)
      prisonerRepository.save(Prisoner().also { it.prisonerNumber = "X12342" }, BLUE)
      prisonerRepository.save(Prisoner().also { it.prisonerNumber = "X12343" }, BLUE)
      prisonerRepository.save(Prisoner().also { it.prisonerNumber = "X12344" }, BLUE)

      await untilCallTo { prisonerRepository.count(BLUE) } matches { it == 4L }
      assertThat(prisonerRepository.count(GREEN)).isEqualTo(0L)
    }

    @Test
    internal fun `will return -1 if index doesn't exist`() {
      assertThat(prisonerRepository.count(BLUE)).isEqualTo(0L)
      prisonerRepository.deleteIndex(BLUE)
      assertThat(prisonerRepository.count(BLUE)).isEqualTo(-1L)
    }
  }

  @Nested
  inner class Get {

    @Test
    internal fun `will get a prisoner in the correct index`() {
      prisonerRepository.save(
        Prisoner().apply { prisonerNumber = "X12345" },
        BLUE,
      )

      assertThat(prisonerRepository.get("X12345", listOf(BLUE))).extracting("prisonerNumber").isEqualTo("X12345")
      assertThat(prisonerRepository.get("X12345", listOf(GREEN))).isNull()
    }

    @Test
    internal fun `will check all supplied indices to find prisoner`() {
      prisonerRepository.save(
        Prisoner().apply { prisonerNumber = "X12345" },
        BLUE,
      )

      assertThat(prisonerRepository.get("X12345", listOf(GREEN, BLUE))).extracting("prisonerNumber").isEqualTo("X12345")
    }

    @Test
    internal fun `will get prisoner`() {
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
    internal fun `will save prisoner in the correct index`() {
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
    internal fun `will save json`() {
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
  inner class Delete {

    @Test
    internal fun `will delete prisoner`() {
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
    internal fun setUp() {
      deletePrisonerIndices()
    }

    @Test
    internal fun `will delete an existing index`() {
      prisonerRepository.createIndex(BLUE)

      assertThat(highLevelClient.indices().exists(GetIndexRequest(BLUE.indexName), RequestOptions.DEFAULT)).isTrue()
      assertThat(highLevelClient.indices().exists(GetIndexRequest(GREEN.indexName), RequestOptions.DEFAULT)).isFalse()

      prisonerRepository.deleteIndex(BLUE)

      assertThat(highLevelClient.indices().exists(GetIndexRequest(BLUE.indexName), RequestOptions.DEFAULT)).isFalse()
      assertThat(highLevelClient.indices().exists(GetIndexRequest(GREEN.indexName), RequestOptions.DEFAULT)).isFalse()
    }

    @Test
    internal fun `will leave the other index alone`() {
      prisonerRepository.createIndex(BLUE)
      prisonerRepository.createIndex(GREEN)

      assertThat(highLevelClient.indices().exists(GetIndexRequest(BLUE.indexName), RequestOptions.DEFAULT)).isTrue()
      assertThat(highLevelClient.indices().exists(GetIndexRequest(GREEN.indexName), RequestOptions.DEFAULT)).isTrue()

      prisonerRepository.deleteIndex(BLUE)

      assertThat(highLevelClient.indices().exists(GetIndexRequest(BLUE.indexName), RequestOptions.DEFAULT)).isFalse()
      assertThat(highLevelClient.indices().exists(GetIndexRequest(GREEN.indexName), RequestOptions.DEFAULT)).isTrue()
    }

    @Test
    internal fun `will not complain if index to delete does not exist`() {
      assertThat(highLevelClient.indices().exists(GetIndexRequest(BLUE.indexName), RequestOptions.DEFAULT)).isFalse()

      prisonerRepository.deleteIndex(BLUE)

      assertThat(highLevelClient.indices().exists(GetIndexRequest(BLUE.indexName), RequestOptions.DEFAULT)).isFalse()
    }
  }

  @Nested
  inner class DoesIndexExist {
    @BeforeEach
    internal fun setUp() {
      deletePrisonerIndices()
    }

    @Test
    internal fun `will report true when index exists`() {
      prisonerRepository.createIndex(BLUE)
      assertThat(prisonerRepository.doesIndexExist(BLUE)).isTrue()
      assertThat(prisonerRepository.doesIndexExist(GREEN)).isFalse()
    }

    @Test
    internal fun `will report false when index does not exists`() {
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
        assertThat(highLevelClient.indices().exists(GetIndexRequest("prisoner"), RequestOptions.DEFAULT)).isTrue()
        assertThat(
          highLevelClient.indices().getAlias(GetAliasesRequest().aliases("prisoner"), RequestOptions.DEFAULT).aliases,
        ).containsKey(GREEN.indexName)
        assertThat(
          highLevelClient.indices().getAlias(GetAliasesRequest().aliases("prisoner"), RequestOptions.DEFAULT).aliases,
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
      internal fun setUp() {
        prisonerRepository.switchAliasIndex(GREEN)
      }

      @Test
      fun `can switch an alias for active index`() {
        prisonerRepository.switchAliasIndex(BLUE)
        assertThat(highLevelClient.indices().exists(GetIndexRequest("prisoner"), RequestOptions.DEFAULT)).isTrue()
        assertThat(
          highLevelClient.indices().getAlias(GetAliasesRequest().aliases("prisoner"), RequestOptions.DEFAULT).aliases,
        ).containsKey(BLUE.indexName)
        assertThat(
          highLevelClient.indices().getAlias(GetAliasesRequest().aliases("prisoner"), RequestOptions.DEFAULT).aliases,
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
      internal fun setUp() {
        prisonerRepository.switchAliasIndex(BLUE)
      }

      @Test
      fun `will keep an alias for active index`() {
        prisonerRepository.switchAliasIndex(BLUE)
        assertThat(highLevelClient.indices().exists(GetIndexRequest("prisoner"), RequestOptions.DEFAULT)).isTrue()
        assertThat(
          highLevelClient.indices().getAlias(GetAliasesRequest().aliases("prisoner"), RequestOptions.DEFAULT).aliases,
        ).containsKey(BLUE.indexName)
        assertThat(
          highLevelClient.indices().getAlias(GetAliasesRequest().aliases("prisoner"), RequestOptions.DEFAULT).aliases,
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
