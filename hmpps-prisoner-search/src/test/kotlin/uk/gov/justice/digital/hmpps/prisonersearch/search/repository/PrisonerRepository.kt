package uk.gov.justice.digital.hmpps.prisonersearch.search.repository

import org.opensearch.OpenSearchStatusException
import org.opensearch.action.admin.indices.delete.DeleteIndexRequest
import org.opensearch.client.RequestOptions
import org.opensearch.client.RestHighLevelClient
import org.opensearch.client.core.CountRequest
import org.opensearch.client.indices.CreateIndexRequest
import org.opensearch.client.indices.GetIndexRequest
import org.slf4j.LoggerFactory
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates.of
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.prisonersearch.common.config.OpenSearchIndexConfiguration.Companion.PRISONER_INDEX
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner

@Repository
class PrisonerRepository(
  private val client: RestHighLevelClient,
  private val openSearchRestTemplate: ElasticsearchOperations,
) {
  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  fun count() = try {
    client.count(CountRequest(PRISONER_INDEX), RequestOptions.DEFAULT).count
  } catch (e: OpenSearchStatusException) {
    // if the index doesn't exist yet then we will get an exception, so catch and move on
    -1
  }

  fun save(prisoner: Prisoner) {
    openSearchRestTemplate.index(IndexQueryBuilder().withObject(prisoner).build(), of(PRISONER_INDEX))
  }

  fun get(prisonerNumber: String): Prisoner? = openSearchRestTemplate.get(prisonerNumber, Prisoner::class.java, of(PRISONER_INDEX))

  fun createIndex() {
    log.info("creating index")
    client.indices().create(CreateIndexRequest(PRISONER_INDEX), RequestOptions.DEFAULT)
    addMapping()
  }

  fun addMapping() {
    openSearchRestTemplate.indexOps(IndexCoordinates.of(PRISONER_INDEX)).apply {
      putMapping(createMapping(Prisoner::class.java))
    }
  }

  fun deleteIndex() {
    log.info("deleting index")
    if (client.indices().exists(GetIndexRequest(PRISONER_INDEX), RequestOptions.DEFAULT)) {
      client.indices().delete(DeleteIndexRequest(PRISONER_INDEX), RequestOptions.DEFAULT)
    } else {
      log.warn("index was never there in the first place")
    }
  }
}
