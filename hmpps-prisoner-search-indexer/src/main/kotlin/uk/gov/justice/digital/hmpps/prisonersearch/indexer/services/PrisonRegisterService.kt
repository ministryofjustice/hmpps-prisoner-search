package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException

@Service
class PrisonRegisterService(
  private val prisonRegisterWebClient: WebClient,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Cacheable("prisons")
  fun getAllPrisons(): List<PrisonDto>? {
    try {
      return prisonRegisterWebClient
        .get()
        .uri("/prisons")
        .header("Content-Type", "application/json")
        .retrieve()
        .bodyToMono(object : ParameterizedTypeReference<List<PrisonDto>>() {})
        .block()
    } catch (ex: WebClientResponseException) {
      log.error("Unable to retrieve prisons from register", ex)
      return null
    }
  }
}

data class PrisonDto(
  val prisonId: String,
  val active: Boolean,
  val male: Boolean,
  val female: Boolean,
)
