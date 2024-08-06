package uk.gov.justice.digital.hmpps.prisonersearch.indexer.repository

import org.opensearch.OpenSearchStatusException
import org.opensearch.action.admin.indices.alias.IndicesAliasesRequest
import org.opensearch.action.admin.indices.alias.get.GetAliasesRequest
import org.opensearch.action.admin.indices.delete.DeleteIndexRequest
import org.opensearch.client.RequestOptions
import org.opensearch.client.RestHighLevelClient
import org.opensearch.client.core.CountRequest
import org.opensearch.client.indices.CreateIndexRequest
import org.opensearch.client.indices.DeleteAliasRequest
import org.opensearch.client.indices.GetIndexRequest
import org.slf4j.LoggerFactory
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.prisonersearch.common.config.OpenSearchIndexConfiguration
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.INCENTIVES_INDEX_STATUS_ID
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Incentive
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.SyncIndex

@Repository
class IncentiveRepository(
  private val client: RestHighLevelClient,
  private val openSearchRestTemplate: ElasticsearchOperations,
) {
  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  fun count(index: SyncIndex) = try {
    client.count(CountRequest(index.incentiveIndexName), RequestOptions.DEFAULT).count
  } catch (e: OpenSearchStatusException) {
    // if the index doesn't exist yet then we will get an exception, so catch and move on
    -1
  }

  fun save(incentive: Incentive, index: SyncIndex) {
    openSearchRestTemplate.index(IndexQueryBuilder().withObject(incentive).build(), index.toIndexCoordinates())
  }

  fun delete(prisonerNumber: String) {
    listOf(SyncIndex.GREEN, SyncIndex.BLUE).forEach {
      openSearchRestTemplate.delete(prisonerNumber, it.toIndexCoordinates())
    }
  }

  fun get(prisonerNumber: String, indices: List<SyncIndex>): Incentive? =
    indices.firstNotNullOfOrNull {
      openSearchRestTemplate.get(prisonerNumber, Incentive::class.java, it.toIndexCoordinates())
    }

  fun createIndex(index: SyncIndex) {
    log.info("creating index {}", index.incentiveIndexName)
    client.indices().create(CreateIndexRequest(index.incentiveIndexName), RequestOptions.DEFAULT)
    addMapping(index)
  }

  fun addMapping(index: SyncIndex) {
    log.info("adding mapping to index {}", index.incentiveIndexName)
    openSearchRestTemplate.indexOps(IndexCoordinates.of(index.incentiveIndexName)).apply {
      putMapping(createMapping(Incentive::class.java))
    }
  }

  fun deleteIndex(index: SyncIndex) {
    log.info("deleting index {}", index.incentiveIndexName)
    if (client.indices().exists(GetIndexRequest(index.incentiveIndexName), RequestOptions.DEFAULT)) {
      client.indices().delete(DeleteIndexRequest(index.incentiveIndexName), RequestOptions.DEFAULT)
    } else {
      log.warn("index {} was never there in the first place", index.incentiveIndexName)
    }
  }

  fun doesIndexExist(index: SyncIndex): Boolean =
    client.indices().exists(GetIndexRequest(index.incentiveIndexName), RequestOptions.DEFAULT)

  fun switchAliasIndex(index: SyncIndex) {
    val alias = client.indices().getAlias(
      GetAliasesRequest().aliases(OpenSearchIndexConfiguration.INCENTIVE_INDEX),
      RequestOptions.DEFAULT,
    )
    client.indices()
      .updateAliases(
        IndicesAliasesRequest().addAliasAction(
          IndicesAliasesRequest.AliasActions(IndicesAliasesRequest.AliasActions.Type.ADD).index(index.incentiveIndexName)
            .alias(OpenSearchIndexConfiguration.INCENTIVE_INDEX),
        ),
        RequestOptions.DEFAULT,
      )

    alias.aliases[index.otherIndex(INCENTIVES_INDEX_STATUS_ID).incentiveIndexName]?.forEach {
      client.indices()
        .deleteAlias(
          DeleteAliasRequest(index.otherIndex(INCENTIVES_INDEX_STATUS_ID).incentiveIndexName, it.alias),
          RequestOptions.DEFAULT,
        )
    }
  }

  fun prisonerAliasIsPointingAt(): Set<String> {
    val alias = client.indices().getAlias(
      GetAliasesRequest().aliases(OpenSearchIndexConfiguration.INCENTIVE_INDEX),
      RequestOptions.DEFAULT,
    )
    return alias.aliases.keys
  }
}

private fun SyncIndex.toIndexCoordinates() = IndexCoordinates.of(this.incentiveIndexName)
