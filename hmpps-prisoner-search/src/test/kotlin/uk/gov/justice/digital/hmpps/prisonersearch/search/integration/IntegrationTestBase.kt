package uk.gov.justice.digital.hmpps.prisonersearch.search.integration

import com.google.gson.Gson
import com.microsoft.applicationinsights.TelemetryClient
import org.junit.jupiter.api.extension.ExtendWith
import org.opensearch.client.RestHighLevelClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.cache.CacheManager
import org.springframework.context.ApplicationContext
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.reactive.server.WebTestClient
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonersearch.common.services.SearchClient
import uk.gov.justice.digital.hmpps.prisonersearch.search.integration.wiremock.AlertsApiExtension
import uk.gov.justice.digital.hmpps.prisonersearch.search.integration.wiremock.HmppsAuthApiExtension
import uk.gov.justice.digital.hmpps.prisonersearch.search.repository.IndexStatusRepository
import uk.gov.justice.digital.hmpps.prisonersearch.search.repository.PrisonerRepository
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.AlertsApiService
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.PrisonersInPrisonService
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.AttributeSearchService
import uk.gov.justice.hmpps.test.kotlin.auth.JwtAuthorisationHelper

@ExtendWith(HmppsAuthApiExtension::class, AlertsApiExtension::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureWebTestClient
abstract class IntegrationTestBase {
  @Autowired
  internal lateinit var jwtAuthHelper: JwtAuthorisationHelper

  @MockitoSpyBean
  internal lateinit var telemetryClient: TelemetryClient

  @MockitoSpyBean
  internal lateinit var attributeSearchService: AttributeSearchService

  @Autowired
  internal lateinit var gson: Gson

  @Autowired
  internal lateinit var jsonMapper: JsonMapper

  @Autowired
  internal lateinit var webTestClient: WebTestClient

  @Autowired
  internal lateinit var prisonerRepository: PrisonerRepository

  @Autowired
  internal lateinit var indexStatusRepository: IndexStatusRepository

  @MockitoSpyBean
  internal lateinit var searchService: PrisonersInPrisonService

  @MockitoSpyBean
  internal lateinit var elasticsearchClient: SearchClient

  @MockitoSpyBean
  internal lateinit var openSearchClient: RestHighLevelClient

  @Autowired
  internal lateinit var cacheManager: CacheManager

  @Autowired
  internal lateinit var context: ApplicationContext

  @Autowired
  internal lateinit var alertsApiService: AlertsApiService

  init {
    // Resolves an issue where Wiremock keeps previous sockets open from other tests causing connection resets
    System.setProperty("http.keepAlive", "false")
  }
  internal fun setAuthorisation(user: String = "prisoner-search-client", roles: List<String> = listOf()): (HttpHeaders) -> Unit = jwtAuthHelper.setAuthorisationHeader(username = user, clientId = "prisoner-search-client", roles = roles)
}
