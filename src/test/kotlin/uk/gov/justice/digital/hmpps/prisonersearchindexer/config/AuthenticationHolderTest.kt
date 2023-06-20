package uk.gov.justice.digital.hmpps.prisonersearchindexer.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt

class AuthenticationHolderTest {
  private val authenticationHolder: AuthenticationHolder = AuthenticationHolder()

  @Test
  fun userAuthenticationCurrentUsername() {
    setAuthentication()
    assertThat(authenticationHolder.currentUsername()).isEqualTo("UserName")
  }

  @Test
  fun userAuthenticationClientID() {
    setAuthentication()
    assertThat(authenticationHolder.currentClientId()).isEqualTo("clientID")
  }

  @AfterEach
  fun afterEach() = SecurityContextHolder.clearContext()

  private fun setAuthentication() {
    val auth: Authentication = AuthAwareAuthenticationToken(
      Mockito.mock(Jwt::class.java),
      "UserName",
      "clientID",
      emptySet(),
    )
    SecurityContextHolder.getContext().authentication = auth
  }
}
