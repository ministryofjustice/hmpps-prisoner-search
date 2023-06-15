package uk.gov.justice.digital.hmpps.prisonersearchindexer.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.config.web.server.invoke
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.web.server.SecurityWebFilterChain

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity(useAuthorizationManager = false)
@EnableCaching
class ResourceServerConfiguration {
  @Bean
  fun springSecurityFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain =
    http {
      // Can't have CSRF protection as requires session
      csrf { disable() }
      authorizeExchange {
        listOf(
          "/webjars/**", "/favicon.ico", "/csrf",
          "/health/**", "/info", "/startup", "/h2-console/**",
          "/v3/api-docs/**", "/swagger-ui.html",
          "/queue-admin/retry-all-dlqs",
          "/maintain-index/check-complete",
        ).forEach { authorize(it, permitAll) }
        authorize(anyExchange, authenticated)
      }
      oauth2ResourceServer { jwt { jwtAuthenticationConverter = AuthAwareTokenConverter() } }
    }

  @Bean
  fun locallyCachedJwtDecoder(
    @Value("\${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") jwkSetUri: String,
    cacheManager: CacheManager,
  ): JwtDecoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).cache(cacheManager.getCache("jwks")).build()
}
