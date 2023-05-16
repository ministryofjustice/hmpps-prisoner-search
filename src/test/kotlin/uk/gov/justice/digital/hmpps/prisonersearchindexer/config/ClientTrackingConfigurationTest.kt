package uk.gov.justice.digital.hmpps.prisonersearchindexer.config

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.prisonersearchindexer.services.JwtAuthHelper

@Import(JwtAuthHelper::class, ClientTrackingWebFilter::class)
@ContextConfiguration(initializers = [ConfigDataApplicationContextInitializer::class])
@ActiveProfiles("test")
@ExtendWith(SpringExtension::class)
class ClientTrackingConfigurationTest {
  @Suppress("SpringJavaInjectionPointsAutowiringInspection")
  @Autowired
  private lateinit var clientTrackingInterceptor: ClientTrackingWebFilter

  @Suppress("SpringJavaInjectionPointsAutowiringInspection")
  @Autowired
  private lateinit var jwtAuthHelper: JwtAuthHelper

  private val webFilterChain: WebFilterChain = mock()

  private val tracer: Tracer = otelTesting.openTelemetry.getTracer("test")

  @BeforeEach
  internal fun setup() {
    whenever(webFilterChain.filter(any())).thenReturn(Mono.empty())
  }

  @Test
  fun shouldAddClientIdAndUserNameToInsightTelemetry() {
    val token = jwtAuthHelper.createJwt("bob")
    val req = MockServerHttpRequest.get("url").header(HttpHeaders.AUTHORIZATION, "Bearer $token")
    tracer.spanBuilder("span").startSpan().run {
      makeCurrent().use { clientTrackingInterceptor.filter(MockServerWebExchange.builder(req).build(), webFilterChain) }
      end()
    }
    otelTesting.assertTraces().hasTracesSatisfyingExactly({ t ->
      t.hasSpansSatisfyingExactly({
        it.hasAttribute(AttributeKey.stringKey("username"), "bob")
        it.hasAttribute(AttributeKey.stringKey("clientId"), "prisoner-offender-search-client")
      },)
    },)
  }

  @Test
  fun shouldAddOnlyClientIdIfUsernameNullToInsightTelemetry() {
    val token = jwtAuthHelper.createJwt(null)
    val req = MockServerHttpRequest.get("url").header(HttpHeaders.AUTHORIZATION, "Bearer $token")
    tracer.spanBuilder("span").startSpan().run {
      makeCurrent().use { clientTrackingInterceptor.filter(MockServerWebExchange.builder(req).build(), webFilterChain) }
      end()
    }
    otelTesting.assertTraces().hasTracesSatisfyingExactly({ t ->
      t.hasSpansSatisfyingExactly({
        it.hasAttribute(AttributeKey.stringKey("clientId"), "prisoner-offender-search-client")
      },)
    },)
  }

  private companion object {
    @JvmStatic
    @RegisterExtension
    private val otelTesting: OpenTelemetryExtension = OpenTelemetryExtension.create()
  }
}
