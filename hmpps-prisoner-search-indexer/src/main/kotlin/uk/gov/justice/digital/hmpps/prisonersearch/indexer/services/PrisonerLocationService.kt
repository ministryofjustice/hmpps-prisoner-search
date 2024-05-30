package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import org.opensearch.action.search.SearchRequest
import org.opensearch.action.search.SearchResponse
import org.opensearch.index.query.QueryBuilders
import org.opensearch.search.builder.SearchSourceBuilder
import org.opensearch.search.fetch.subphase.FetchSourceContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.common.config.OpenSearchIndexConfiguration

@Service
class PrisonerLocationService(private val searchClient: SearchClient) {
  fun findPrisoners(prisonId: String, cellLocation: String): List<String> {
    val searchSourceBuilder = SearchSourceBuilder().query(
      QueryBuilders.boolQuery()
        .must(QueryBuilders.termQuery("prisonId", prisonId))
        .must(QueryBuilders.termQuery("cellLocation.keyword", cellLocation)),
    ).fetchSource(FetchSourceContext.DO_NOT_FETCH_SOURCE)
    val searchRequest = SearchRequest(arrayOf(OpenSearchIndexConfiguration.PRISONER_INDEX), searchSourceBuilder)
    return getSearchResult(searchClient.search(searchRequest)).also {
      log.debug("Search found {} hits for {} and {}", it.size, prisonId, cellLocation)
    }
  }

  private fun getSearchResult(response: SearchResponse): List<String> = response.hits.hits.map { it.id }

  private companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
