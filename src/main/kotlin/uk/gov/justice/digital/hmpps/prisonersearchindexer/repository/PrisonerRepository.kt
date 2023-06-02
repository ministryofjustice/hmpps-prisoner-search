package uk.gov.justice.digital.hmpps.prisonersearchindexer.repository

import org.opensearch.action.admin.indices.alias.IndicesAliasesRequest
import org.opensearch.action.admin.indices.alias.get.GetAliasesRequest
import org.opensearch.action.admin.indices.delete.DeleteIndexRequest
import org.opensearch.client.RequestOptions
import org.opensearch.client.RestHighLevelClient
import org.opensearch.client.indices.CreateIndexRequest
import org.opensearch.client.indices.DeleteAliasRequest
import org.opensearch.client.indices.GetIndexRequest
import org.opensearch.data.client.orhlc.OpenSearchRestTemplate
import org.slf4j.LoggerFactory
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.prisonersearchindexer.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearchindexer.model.SyncIndex

@Repository
class PrisonerRepository(
  private val client: RestHighLevelClient,
  private val restTemplate: OpenSearchRestTemplate,
) {
  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  fun save(prisoner: Prisoner, index: SyncIndex) {
    restTemplate.index(IndexQueryBuilder().withObject(prisoner).build(), IndexCoordinates.of(index.indexName))
  }

  fun createIndex(index: SyncIndex) {
    log.info("creating index {}", index.indexName)
    client.indices().create(CreateIndexRequest(index.indexName), RequestOptions.DEFAULT)
  }

  fun deleteIndex(index: SyncIndex) {
    log.info("deleting index {}", index.indexName)
    if (client.indices().exists(GetIndexRequest(index.indexName), RequestOptions.DEFAULT)) {
      client.indices().delete(DeleteIndexRequest(index.indexName), RequestOptions.DEFAULT)
    } else {
      log.warn("index {} was never there in the first place", index.indexName)
    }
  }

  fun doesIndexExist(index: SyncIndex): Boolean =
    client.indices().exists(GetIndexRequest(index.indexName), RequestOptions.DEFAULT)

  fun switchAliasIndex(index: SyncIndex) {
    val alias = client.indices().getAlias(GetAliasesRequest().aliases("prisoner"), RequestOptions.DEFAULT)
    client.indices()
      .updateAliases(
        IndicesAliasesRequest().addAliasAction(
          IndicesAliasesRequest.AliasActions(IndicesAliasesRequest.AliasActions.Type.ADD).index(index.indexName)
            .alias("prisoner"),
        ),
        RequestOptions.DEFAULT,
      )

    alias.aliases[index.otherIndex().indexName]?.forEach {
      client.indices()
        .deleteAlias(DeleteAliasRequest(index.otherIndex().indexName, it.alias), RequestOptions.DEFAULT)
    }
  }

  fun prisonerAliasIsPointingAt(): Set<String> {
    val alias = client.indices().getAlias(GetAliasesRequest().aliases("prisoner"), RequestOptions.DEFAULT)
    return alias.aliases.keys
  }
}
