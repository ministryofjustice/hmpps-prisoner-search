package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import com.microsoft.applicationinsights.TelemetryClient
import org.opensearch.action.search.ClearScrollRequest
import org.opensearch.action.search.SearchRequest
import org.opensearch.action.search.SearchResponse
import org.opensearch.action.search.SearchScrollRequest
import org.opensearch.client.RequestOptions
import org.opensearch.client.RestHighLevelClient
import org.opensearch.common.unit.TimeValue
import org.opensearch.search.Scroll
import org.opensearch.search.builder.SearchSourceBuilder
import org.opensearch.search.fetch.subphase.FetchSourceContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.TelemetryEvents
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.repository.PrisonerRepository

@Service
class CompareIndexService(
  private val indexStatusService: IndexStatusService,
  private val openSearchClient: RestHighLevelClient,
  private val telemetryClient: TelemetryClient,
  private val nomisService: NomisService,
  private val prisonerRepository: PrisonerRepository,
) {

  private companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
    private const val cutoff = 50
  }

  fun doIndexSizeCheck() {
    val start = System.currentTimeMillis()
    val totalIndexNumber = prisonerRepository.count(indexStatusService.getIndexStatus().currentIndex)
    val totalNomisNumber = nomisService.getTotalNumberOfPrisoners()
    val end = System.currentTimeMillis()

    telemetryClient.trackEvent(
      TelemetryEvents.COMPARE_INDEX_SIZE,
      mapOf(
        "timeMs" to (end - start).toString(),
        "totalNomis" to totalNomisNumber.toString(),
        "totalIndex" to totalIndexNumber.toString(),
      ),
    )
  }

  @Async
  fun doCompareByIds() {
    try {
      val start = System.currentTimeMillis()
      val (onlyInIndex, onlyInNomis) = compareIndex()
      val end = System.currentTimeMillis()
      telemetryClient.trackEvent(
        TelemetryEvents.COMPARE_INDEX_IDS,
        mapOf(
          "onlyInIndex" to toLogMessage(onlyInIndex),
          "onlyInNomis" to toLogMessage(onlyInNomis),
          "timeMs" to (end - start).toString(),
        ),
      )
      log.info("End of doCompare()")
    } catch (e: Exception) {
      log.error("compare failed", e)
    }
  }
  fun compareIndex(): Pair<List<String>, List<String>> {
    val allNomis = nomisService.getPrisonerNumbers(0, 10000000).sorted()

    val scroll = Scroll(TimeValue.timeValueMinutes(1L))
    val searchResponse = setupIndexSearch(scroll)

    var scrollId = searchResponse.scrollId
    var searchHits = searchResponse.hits.hits

    val allIndex = mutableListOf<String>()

    while (!searchHits.isNullOrEmpty()) {
      allIndex.addAll(searchHits.map { it.id })

      val scrollRequest = SearchScrollRequest(scrollId)
      scrollRequest.scroll(scroll)
      val scrollResponse = openSearchClient.scroll(scrollRequest, RequestOptions.DEFAULT)
      scrollId = scrollResponse.scrollId
      searchHits = scrollResponse.hits.hits
    }
    log.info("compareIndex(): allIndex=${allIndex.size}, allNomis=${allNomis.size}")

    val clearScrollRequest = ClearScrollRequest()
    clearScrollRequest.addScrollId(scrollId)
    val clearScrollResponse = openSearchClient.clearScroll(clearScrollRequest, RequestOptions.DEFAULT)
    log.info("clearScroll isSucceeded=${clearScrollResponse.isSucceeded}, numFreed=${clearScrollResponse.numFreed}")

    allIndex.sort()

    val onlyInIndex = allIndex - allNomis.toSet()
    val onlyInNomis = allNomis - allIndex.toSet()

    return Pair(onlyInIndex, onlyInNomis)
  }

  private fun setupIndexSearch(scroll: Scroll): SearchResponse {
    val searchSourceBuilder = SearchSourceBuilder().apply {
      fetchSource(FetchSourceContext.DO_NOT_FETCH_SOURCE)
      size(2000)
    }
    val searchRequest = SearchRequest(arrayOf(indexStatusService.getIndexStatus().currentIndex.indexName), searchSourceBuilder)
    searchRequest.scroll(scroll)
    return openSearchClient.search(searchRequest, RequestOptions.DEFAULT)
  }

  private fun toLogMessage(onlyList: List<String>): String =
    if (onlyList.size <= cutoff) onlyList.toString() else onlyList.slice(IntRange(0, cutoff)).toString() + "..."
}
