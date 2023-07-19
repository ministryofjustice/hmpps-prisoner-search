package uk.gov.justice.digital.hmpps.prisonersearch.search

import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IndexStatus
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.SyncIndex
import uk.gov.justice.digital.hmpps.prisonersearch.search.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonersearch.search.model.RestResponsePage
import uk.gov.justice.digital.hmpps.prisonersearch.search.repository.IndexStatusRepository
import uk.gov.justice.digital.hmpps.prisonersearch.search.repository.PrisonerRepository
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.GlobalSearchCriteria
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.PrisonSearch
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.ReleaseDateSearch
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.RestrictedPatientSearchCriteria
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.SearchCriteria
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.dto.KeywordRequest
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.dto.MatchRequest
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.dto.PossibleMatchCriteria

/**
 * Test class to initialise the standard set of search data only once.
 * Subclasses will get the same set of search data and we have implemented our own custom junit orderer to ensure that
 * no other type of tests run inbetween and cause a re-index to ruin our data.
 */
abstract class AbstractSearchDataIntegrationTest : IntegrationTestBase() {

  private companion object {
    private var initialiseSearchData = true
  }

  @Autowired
  lateinit var prisonerRepository: PrisonerRepository

  @Autowired
  lateinit var indexStatusRepository: IndexStatusRepository

  @BeforeEach
  fun setup() {
    if (initialiseSearchData) {
      deletePrisonerIndex()
      createPrisonerIndex()
      initialiseIndexStatus()
      loadPrisonerData()

      initialiseSearchData = false
    }
  }

  fun createPrisonerIndex() = prisonerRepository.createIndex(SyncIndex.GREEN)

  fun deletePrisonerIndex() = prisonerRepository.deleteIndex(SyncIndex.GREEN)

  fun initialiseIndexStatus() {
    indexStatusRepository.deleteAll()
    indexStatusRepository.save(IndexStatus(currentIndex = SyncIndex.GREEN))
    prisonerRepository.switchAliasIndex(SyncIndex.GREEN)
  }

  private fun loadPrisonerData() {
    val prisoners = listOf(
      "A1090AA", "A7089EZ", "A7089FB", "A7089FX", "A7090AB", "A7090AD", "A7090AF", "A7090BB",
      "A7090BD", "A7090BF", "A9999AB", "A9999RA", "A9999RC", "A7089EY", "A7089FA", "A7089FC", "A7090AA", "A7090AC",
      "A7090AE", "A7090BA", "A7090BC", "A7090BE", "A9999AA", "A9999AC", "A9999RB",
    )
    prisoners.forEach {
      val sourceAsString = "/prisoners/prisoner$it.json".readResourceAsText()
      val prisoner = gson.fromJson(sourceAsString, Prisoner::class.java)
      prisonerRepository.save(prisoner, SyncIndex.GREEN)
    }
    waitForPrisonerLoading(prisoners.size)
  }

  private fun waitForPrisonerLoading(expectedCount: Int) {
    await untilCallTo {
      prisonerRepository.count(SyncIndex.GREEN)
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
      .expectBody().json(fileAssert.readResourceAsText(), true)
  }

  fun search(searchCriteria: SearchCriteria, fileAssert: String) {
    search(searchCriteria).json(fileAssert.readResourceAsText())
  }

  fun search(searchCriteria: SearchCriteria): WebTestClient.BodyContentSpec =
    webTestClient.post().uri("/prisoner-search/match-prisoners")
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
      .body(BodyInserters.fromValue(gson.toJson(restrictedPatientSearchCriteria)))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
      .expectBody().json(fileAssert.readResourceAsText())
  }
}

fun String.readResourceAsText(): String = AbstractSearchDataIntegrationTest::class.java.getResource(this)!!.readText()
