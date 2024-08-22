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
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.SyncIndex
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
  private val prisonerSynchroniserService: PrisonerSynchroniserService,
  private val prisonerDifferenceService: PrisonerDifferenceService,
) {
  private companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
    private const val CUTOFF = 50
    private val scroll = Scroll(TimeValue.timeValueMinutes(1L))
  }

  fun doIndexSizeCheck(): SizeCheck {
    val start = System.currentTimeMillis()
    val totalIndexNumber = prisonerRepository.count(indexStatusService.getIndexStatus().currentIndex)
    val totalNomisNumber = nomisService.getTotalNumberOfPrisoners()
    val end = System.currentTimeMillis()

    return SizeCheck(timeMs = end - start, totalNomis = totalNomisNumber, totalIndex = totalIndexNumber).also {
      telemetryClient.trackEvent(TelemetryEvents.COMPARE_INDEX_SIZE, it.toMap())
    }
  }

  data class SizeCheck(val timeMs: Long, val totalNomis: Long, val totalIndex: Long) {
    fun toMap() = mapOf(
      "timeMs" to timeMs.toString(),
      "totalNomis" to totalNomis.toString(),
      "totalIndex" to totalIndex.toString(),
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

    val currentIndex = indexStatusService.getIndexStatus().currentIndex
    val searchResponse = setupIndexSearch(currentIndex)

    var scrollId = searchResponse.scrollId
    var searchHits = searchResponse.hits.hits

    val allIndex = mutableListOf<String>()

    while (!searchHits.isNullOrEmpty()) {
      allIndex.addAll(searchHits.map { it.id })

      val scrollRequest = SearchScrollRequest(scrollId).apply { scroll(scroll) }
      val scrollResponse = openSearchClient.scroll(scrollRequest, RequestOptions.DEFAULT)
      scrollId = scrollResponse.scrollId
      searchHits = scrollResponse.hits.hits
    }
    log.info("compareIndex(): allIndex=${allIndex.size}, allNomis=${allNomis.size}")

    clearIndexSearch(scrollId)

    allIndex.sort()

    val onlyInIndex = allIndex - allNomis.toSet()
    val onlyInNomis = allNomis - allIndex.toSet()

    return Pair(onlyInIndex, onlyInNomis)
  }

  @Async
  fun compareOldAndNewIndex() {
    val start = System.currentTimeMillis()
    val currentIndex = indexStatusService.getIndexStatus().currentIndex
    val currentCount = prisonerRepository.count(currentIndex)
    val redCount = prisonerRepository.count(SyncIndex.RED)
    telemetryClient.trackEvent(
      TelemetryEvents.RED_COMPARE_INDEX_SIZE,
      mapOf("currentCount" to currentCount.toString(), "redCount" to redCount.toString()),
    )

    val searchResponse = setupIndexSearch(currentIndex, FetchSourceContext.FETCH_SOURCE)

    var scrollId = searchResponse.scrollId
    var searchHits = searchResponse.hits.hits

    while (!searchHits.isNullOrEmpty()) {
      searchHits.forEach { hit ->
        val prisonerRed = prisonerRepository.getRaw(hit.id, SyncIndex.RED)
        if (!prisonerRed.isExists) {
          telemetryClient.trackEvent(
            TelemetryEvents.RED_DIFFERENCE_MISSING,
            mapOf("original" to hit.sourceAsString),
          )
        } else if (hit.sourceAsString != prisonerRed.sourceAsString) {
          log.info("Mismatch for prisoner ${hit.id}:\n${hit.sourceAsString}\n${prisonerRed.sourceAsString}")
          telemetryClient.trackEvent(
            TelemetryEvents.RED_DIFFERENCE_REPORTED,
            mapOf("original" to hit.sourceAsString, "red" to prisonerRed.sourceAsString),
          )
        }
      }

      val scrollRequest = SearchScrollRequest(scrollId).apply { scroll(scroll) }
      val scrollResponse = openSearchClient.scroll(scrollRequest, RequestOptions.DEFAULT)

      scrollId = scrollResponse.scrollId
      searchHits = scrollResponse.hits.hits
    }
    clearIndexSearch(scrollId)
    val end = System.currentTimeMillis()
    log.info("compareOldAndNewIndex() took ${end - start}ms")
  }

  fun comparePrisoner(prisonerNumber: String) =
    nomisService.getOffender(prisonerNumber)?.let { ob ->
      val calculated = prisonerSynchroniserService.translate(ob)
      val existing = prisonerRepository.get(ob.offenderNo, listOf(indexStatusService.getIndexStatus().currentIndex))

      prisonerDifferenceService.reportDifferencesDetails(existing, calculated)
    }

  private fun setupIndexSearch(
    index: SyncIndex,
    fetchSource: FetchSourceContext = FetchSourceContext.DO_NOT_FETCH_SOURCE,
  ): SearchResponse {
    val searchSourceBuilder = SearchSourceBuilder().apply {
      fetchSource(fetchSource)
      size(2000)
    }
    val searchRequest = SearchRequest(
      arrayOf(index.indexName),
      searchSourceBuilder,
    ).apply {
      scroll(scroll)
    }
    return openSearchClient.search(searchRequest, RequestOptions.DEFAULT)
  }

  private fun clearIndexSearch(scrollId: String?) {
    val clearScrollRequest = ClearScrollRequest().apply { addScrollId(scrollId) }
    val clearScrollResponse = openSearchClient.clearScroll(clearScrollRequest, RequestOptions.DEFAULT)
    log.info("clearScroll isSucceeded=${clearScrollResponse.isSucceeded}, numFreed=${clearScrollResponse.numFreed}")
  }

  private fun toLogMessage(onlyList: List<String>): String =
    if (onlyList.size <= CUTOFF) onlyList.toString() else onlyList.slice(IntRange(0, CUTOFF)).toString() + "..."
}
