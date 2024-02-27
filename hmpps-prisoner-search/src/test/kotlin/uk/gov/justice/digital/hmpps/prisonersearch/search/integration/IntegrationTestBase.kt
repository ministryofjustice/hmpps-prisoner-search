package uk.gov.justice.digital.hmpps.prisonersearch.search.integration

import com.google.gson.Gson
import com.microsoft.applicationinsights.TelemetryClient
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.prisonersearch.search.integration.wiremock.HmppsAuthApiExtension
import uk.gov.justice.digital.hmpps.prisonersearch.search.integration.wiremock.PrisonApiExtension
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.JwtAuthHelper
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.AttributeSearchService
import java.time.Duration

@ExtendWith(HmppsAuthApiExtension::class, PrisonApiExtension::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
abstract class IntegrationTestBase {
  @Autowired
  internal lateinit var jwtAuthHelper: JwtAuthHelper

  @SpyBean
  lateinit var telemetryClient: TelemetryClient

  @SpyBean
  lateinit var attributeSearchService: AttributeSearchService

  @Autowired
  lateinit var gson: Gson

  @Autowired
  internal lateinit var webTestClient: WebTestClient

  init {
    SecurityContextHolder.getContext().authentication = TestingAuthenticationToken("user", "pw")
    // Resolves an issue where Wiremock keeps previous sockets open from other tests causing connection resets
    System.setProperty("http.keepAlive", "false")
  }
  internal fun Any.asJson() = gson.toJson(this)

  internal fun setAuthorisation(user: String = "prisoner-search-client", roles: List<String> = listOf()): (HttpHeaders) -> Unit {
    val token = jwtAuthHelper.createJwt(
      subject = user,
      scope = listOf("read"),
      expiryTime = Duration.ofHours(1L),
      roles = roles,
    )
    return { it.set(HttpHeaders.AUTHORIZATION, "Bearer $token") }
  }
}
