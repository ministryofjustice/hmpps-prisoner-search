package uk.gov.justice.digital.hmpps.prisonersearch.search.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.gson.Gson
import com.microsoft.applicationinsights.TelemetryClient
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cache.CacheManager
import org.springframework.context.ApplicationContext
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.prisonersearch.common.services.SearchClient
import uk.gov.justice.digital.hmpps.prisonersearch.search.integration.wiremock.HmppsAuthApiExtension
import uk.gov.justice.digital.hmpps.prisonersearch.search.integration.wiremock.PrisonApiExtension
import uk.gov.justice.digital.hmpps.prisonersearch.search.repository.IndexStatusRepository
import uk.gov.justice.digital.hmpps.prisonersearch.search.repository.PrisonerRepository
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.PrisonApiService
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.PrisonersInPrisonService
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.AttributeSearchService
import uk.gov.justice.hmpps.test.kotlin.auth.JwtAuthorisationHelper

@ExtendWith(HmppsAuthApiExtension::class, PrisonApiExtension::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
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
  internal lateinit var objectMapper: ObjectMapper

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

  @Autowired
  internal lateinit var cacheManager: CacheManager

  @Autowired
  internal lateinit var context: ApplicationContext

  @Autowired
  internal lateinit var prisonApiService: PrisonApiService

  init {
    // Resolves an issue where Wiremock keeps previous sockets open from other tests causing connection resets
    System.setProperty("http.keepAlive", "false")
  }
  internal fun Any.asJson() = gson.toJson(this)

  internal fun setAuthorisation(user: String = "prisoner-search-client", roles: List<String> = listOf()): (HttpHeaders) -> Unit =
    jwtAuthHelper.setAuthorisationHeader(username = user, clientId = "prisoner-search-client", roles = roles)
}
