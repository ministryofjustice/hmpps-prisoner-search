package uk.gov.justice.digital.hmpps.prisonersearch.search.config

import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonersearch.common.config.AuthAwareAuthenticationToken

@Component
class AuthenticationHolder {
  val authentication: Authentication
    get() = SecurityContextHolder.getContext().authentication

  fun currentUsername(): String? =
    when (authentication) {
      is AuthAwareAuthenticationToken -> (authentication as AuthAwareAuthenticationToken).userName
      else -> "anonymous"
    }

  fun currentClientId(): String? =
    when (authentication) {
      is AuthAwareAuthenticationToken -> (authentication as AuthAwareAuthenticationToken).clientId
      else -> "anonymous"
    }
}
