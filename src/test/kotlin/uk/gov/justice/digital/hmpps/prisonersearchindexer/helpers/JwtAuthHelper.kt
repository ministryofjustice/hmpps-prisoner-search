package uk.gov.justice.digital.hmpps.prisonersearchindexer.helpers

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpHeaders
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.stereotype.Component
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.time.Duration
import java.util.Date
import java.util.UUID

@Component
class JwtAuthHelper {
  private val keyPair: KeyPair

  init {
    val gen = KeyPairGenerator.getInstance("RSA")
    gen.initialize(2048)
    keyPair = gen.generateKeyPair()
  }

  fun setAuthorisation(user: String = "prisoner-search-indexer-client", roles: List<String> = listOf()): (HttpHeaders) -> Unit {
    val token = createJwt(
      subject = user,
      scope = listOf("read"),
      expiryTime = Duration.ofHours(1L),
      roles = roles,
    )
    return { it.set(HttpHeaders.AUTHORIZATION, "Bearer $token") }
  }

  @Bean
  @Primary
  fun jwtDecoder(): ReactiveJwtDecoder = NimbusReactiveJwtDecoder.withPublicKey(keyPair.public as RSAPublicKey).build()

  fun createJwt(
    subject: String? = null,
    scope: List<String>? = listOf(),
    roles: List<String>? = listOf(),
    expiryTime: Duration = Duration.ofHours(1),
    jwtId: String = UUID.randomUUID().toString(),
  ): String {
    val claims = mutableMapOf<String, Any?>("client_id" to "prisoner-search-client")
      .apply {
        subject?.let { this["user_name"] = subject }
        roles?.let { this["authorities"] = roles }
        scope?.let { this["scope"] = scope }
      }
    return Jwts.builder()
      .setId(jwtId)
      .setSubject(subject)
      .addClaims(claims)
      .setExpiration(Date(System.currentTimeMillis() + expiryTime.toMillis()))
      .signWith(keyPair.private, SignatureAlgorithm.RS256)
      .compact()
  }
}
