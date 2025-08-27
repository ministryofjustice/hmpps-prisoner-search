package uk.gov.justice.digital.hmpps.prisonersearch.search

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IndexStatus
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.search.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonersearch.search.model.PrisonerBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.search.model.RestResponsePage
import uk.gov.justice.digital.hmpps.prisonersearch.search.model.toPrisoner
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.GlobalSearchCriteria
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.PrisonSearch
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.ReleaseDateSearch
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.RestrictedPatientSearchCriteria
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.SearchCriteria
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.dto.KeywordRequest
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.dto.MatchRequest
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.dto.PossibleMatchCriteria

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractSearchIntegrationTest : IntegrationTestBase() {

  @BeforeAll
  fun setup() {
    log.info("Initialising search data")
    deletePrisonerIndex()
    createPrisonerIndex()
    initialiseIndexStatus()
    loadPrisonerData()
  }

  fun createPrisonerIndex() = prisonerRepository.createIndex()

  fun deletePrisonerIndex() = prisonerRepository.deleteIndex()

  fun initialiseIndexStatus() {
    indexStatusRepository.deleteAll()
    indexStatusRepository.save(IndexStatus())
  }

  fun loadPrisonerData() {
    val prisoners = listOf(
      "A1090AA", "A7089EZ", "A7089FB", "A7089FX", "A7090AB", "A7090AD", "A7090AF", "A7090BB",
      "A7090BD", "A7090BF", "A9999AB", "A9999RA", "A9999RC", "A7089EY", "A7089FA", "A7089FC", "A7090AA", "A7090AC",
      "A7090AE", "A7090BA", "A7090BC", "A7090BE", "A9999AA", "A9999AC", "A9999RB",
    )
    prisoners.forEach {
      val sourceAsString = "/prisoners/prisoner$it.json".readResourceAsText()
      val prisoner = gson.fromJson(sourceAsString, Prisoner::class.java)
      prisonerRepository.save(prisoner)
    }
    waitForPrisonerLoading(prisoners.size)
  }

  fun loadPrisonersFromBuilders(prisonerBuilders: List<PrisonerBuilder>) = loadPrisoners(prisonerBuilders.map { it.toPrisoner() })

  fun loadPrisoners(prisoners: List<Prisoner>) {
    prisoners.forEach { prisoner ->
      prisonerRepository.save(prisoner)
    }
    waitForPrisonerLoading(prisoners.size)
  }

  protected fun waitForPrisonerLoading(expectedCount: Int) {
    await untilCallTo {
      prisonerRepository.count()
    } matches { it == expectedCount.toLong() }
  }

  fun keywordSearch(
    keywordRequest: KeywordRequest,
    expectedCount: Int = 0,
    expectedPrisoners: List<String> = emptyList(),
  ) {
    val response = webTestClient.post().uri("/keyword")
      .body(BodyInserters.fromValue(gson.toJson(keywordRequest)))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
      .expectBody(RestResponsePage::class.java)
      .returnResult().responseBody

    assertThat(response.numberOfElements).isEqualTo(expectedCount)
    assertThat(response.content).size().isEqualTo(expectedPrisoners.size)
    assertThat(response.content).extracting("prisonerNumber").containsAll(expectedPrisoners)
  }

  fun singlePrisonSearch(prisonSearch: PrisonSearch, fileAssert: String) {
    webTestClient.post().uri("/prisoner-search/match")
      .body(BodyInserters.fromValue(gson.toJson(prisonSearch)))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
      .expectBody().json(fileAssert.readResourceAsText())
  }

  fun prisonerMatch(matchRequest: MatchRequest, fileAssert: String) {
    webTestClient.post().uri("/match-prisoners")
      .body(BodyInserters.fromValue(gson.toJson(matchRequest)))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
      .expectBody().json(fileAssert.readResourceAsText())
  }

  fun possibleMatch(matchRequest: PossibleMatchCriteria, fileAssert: String) {
    webTestClient.post().uri("/prisoner-search/possible-matches")
      .body(BodyInserters.fromValue(gson.toJson(matchRequest)))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
      .expectBody().json(fileAssert.readResourceAsText(), JsonCompareMode.STRICT)
  }

  fun search(searchCriteria: SearchCriteria, fileAssert: String) {
    search(searchCriteria).json(fileAssert.readResourceAsText())
  }

  fun search(searchCriteria: SearchCriteria): WebTestClient.BodyContentSpec = webTestClient.post().uri("/prisoner-search/match-prisoners")
    .body(BodyInserters.fromValue(gson.toJson(searchCriteria)))
    .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
    .header("Content-Type", "application/json")
    .exchange()
    .expectStatus().isOk
    .expectBody()

  fun globalSearch(globalSearchCriteria: GlobalSearchCriteria, fileAssert: String) {
    webTestClient.post().uri("/global-search")
      .body(BodyInserters.fromValue(gson.toJson(globalSearchCriteria)))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
      .expectBody().json(fileAssert.readResourceAsText())
  }
  fun globalSearchPagination(globalSearchCriteria: GlobalSearchCriteria, size: Long, page: Long, fileAssert: String) {
    webTestClient.post().uri("/global-search?size=$size&page=$page")
      .body(BodyInserters.fromValue(gson.toJson(globalSearchCriteria)))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
      .expectBody().json(fileAssert.readResourceAsText())
  }

  fun searchByReleaseDate(searchCriteria: ReleaseDateSearch, fileAssert: String) {
    webTestClient.post().uri("/prisoner-search/release-date-by-prison")
      .body(BodyInserters.fromValue(gson.toJson(searchCriteria)))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
      .expectBody().json(fileAssert.readResourceAsText())
  }

  fun searchByReleaseDatePagination(searchCriteria: ReleaseDateSearch, size: Long, page: Long, fileAssert: String) {
    webTestClient.post().uri("/prisoner-search/release-date-by-prison?size=$size&page=$page")
      .body(BodyInserters.fromValue(gson.toJson(searchCriteria)))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
      .expectBody().json(fileAssert.readResourceAsText())
  }
  fun prisonSearch(prisonId: String, fileAssert: String, includeRestrictedPatients: Boolean = false) {
    webTestClient.get().uri("/prisoner-search/prison/$prisonId?include-restricted-patients=$includeRestrictedPatients")
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
      .expectBody().json(fileAssert.readResourceAsText())
  }

  fun prisonSearchPagination(prisonId: String, size: Long, page: Long, fileAssert: String) {
    webTestClient.get().uri("/prisoner-search/prison/$prisonId?size=$size&page=$page")
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
      .expectBody().json(fileAssert.readResourceAsText())
  }

  fun getPrisoner(id: String, fileAssert: String) {
    webTestClient.get().uri("/prisoner/$id")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
      .expectBody().json(fileAssert.readResourceAsText())
  }

  fun getPrisonerSearchCorePerson(id: String, fileAssert: String) {
    webTestClient.get().uri("/prisoner/$id")
      .headers(setAuthorisation(roles = listOf("PRISONER_SEARCH__PRISONER__RO")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
      .expectBody().json(fileAssert.readResourceAsText())
  }

  fun restrictedPatientSearch(restrictedPatientSearchCriteria: RestrictedPatientSearchCriteria, fileAssert: String) {
    webTestClient.post().uri("/restricted-patient-search/match-restricted-patients")
      .body(BodyInserters.fromValue(gson.toJson(restrictedPatientSearchCriteria)))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
      .expectBody().json(fileAssert.readResourceAsText())
  }

  fun restrictedPatientSearchPagination(
    restrictedPatientSearchCriteria: RestrictedPatientSearchCriteria,
    size: Long,
    page: Long,
    fileAssert: String,
  ) {
    webTestClient.post().uri("/restricted-patient-search/match-restricted-patients?size=$size&page=$page")
      .body(BodyInserters.fromValue(restrictedPatientSearchCriteria))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
      .expectBody().json(fileAssert.readResourceAsText())
  }

  protected fun forceElasticError() {
    doThrow(RuntimeException("gone wrong")).whenever(elasticsearchClient).search(any())
  }

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}

fun String.readResourceAsText(): String = AbstractSearchDataIntegrationTest::class.java.getResource(this)!!.readText()
