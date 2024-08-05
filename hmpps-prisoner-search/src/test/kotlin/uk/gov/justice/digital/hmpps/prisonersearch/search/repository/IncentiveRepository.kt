package uk.gov.justice.digital.hmpps.prisonersearch.search.repository

import org.opensearch.OpenSearchStatusException
import org.opensearch.action.admin.indices.alias.IndicesAliasesRequest
import org.opensearch.action.admin.indices.delete.DeleteIndexRequest
import org.opensearch.client.RequestOptions
import org.opensearch.client.RestHighLevelClient
import org.opensearch.client.core.CountRequest
import org.opensearch.client.indices.CreateIndexRequest
import org.opensearch.client.indices.GetIndexRequest
import org.slf4j.LoggerFactory
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.prisonersearch.common.config.OpenSearchIndexConfiguration
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.SyncIndex
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner

@Repository
class IncentiveRepository(
  private val client: RestHighLevelClient,
  private val openSearchRestTemplate: ElasticsearchOperations,
) {
  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  fun count(index: SyncIndex) = try {
    client.count(CountRequest(index.indexName), RequestOptions.DEFAULT).count
  } catch (e: OpenSearchStatusException) {
    // if the index doesn't exist yet then we will get an exception, so catch and move on
    -1
  }

  fun save(prisoner: Prisoner, index: SyncIndex) {
    openSearchRestTemplate.index(IndexQueryBuilder().withObject(prisoner).build(), index.toIndexCoordinates())
  }

  fun get(prisonerNumber: String, indices: List<SyncIndex>): Prisoner? =
    indices.firstNotNullOfOrNull {
      openSearchRestTemplate.get(prisonerNumber, Prisoner::class.java, it.toIndexCoordinates())
    }

  fun delete(prisonerNumber: String, index: SyncIndex) {
    openSearchRestTemplate.delete(prisonerNumber, index.toIndexCoordinates())
  }

  fun createIndex(index: SyncIndex) {
    log.info("creating index {}", index.indexName)
    client.indices().create(CreateIndexRequest(index.indexName), RequestOptions.DEFAULT)
    addMapping(index)
  }

  fun addMapping(index: SyncIndex) {
    openSearchRestTemplate.indexOps(IndexCoordinates.of(index.indexName)).apply {
      putMapping(createMapping(Prisoner::class.java))
    }
  }

  fun deleteIndex(index: SyncIndex) {
    log.info("deleting index {}", index.indexName)
    if (client.indices().exists(GetIndexRequest(index.indexName), RequestOptions.DEFAULT)) {
      client.indices().delete(DeleteIndexRequest(index.indexName), RequestOptions.DEFAULT)
    } else {
      log.warn("index {} was never there in the first place", index.indexName)
    }
  }

  fun switchAliasIndex(index: SyncIndex) {
    client.indices()
      .updateAliases(
        IndicesAliasesRequest().addAliasAction(
          IndicesAliasesRequest.AliasActions(IndicesAliasesRequest.AliasActions.Type.ADD).index(index.indexName)
            .alias(OpenSearchIndexConfiguration.PRISONER_INDEX),
        ),
        RequestOptions.DEFAULT,
      )
  }
}
