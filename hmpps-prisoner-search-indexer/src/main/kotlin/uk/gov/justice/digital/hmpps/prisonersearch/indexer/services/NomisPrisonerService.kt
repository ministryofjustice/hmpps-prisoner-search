package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClientRequest
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.nomisprisoner.model.RootOffenderIdRange
import java.time.Duration

@Service
class NomisPrisonerService(
  private val nomisApiWebClient: WebClient,
) {
  fun getAllPrisonersIdRanges(active: Boolean, size: Int = 10) = nomisApiWebClient.get()
    .uri {
      it.path("/search/prisoners/id-ranges")
        .queryParam("active", active)
        .queryParam("size", size)
        .build()
    }
    .httpRequest {
      it.getNativeRequest<HttpClientRequest>().responseTimeout(Duration.ofMinutes(1))
    }
    .retrieve()
    .bodyToMono(object : ParameterizedTypeReference<List<RootOffenderIdRange>>() {})
    .block()!!

  fun getPrisonNumbers(active: Boolean, fromRootOffenderId: Long, toRootOffenderId: Long) = nomisApiWebClient.get()
    .uri {
      it.path("/search/prisoners/ids")
        .queryParam("active", active)
        .queryParam("fromRootOffenderId", fromRootOffenderId)
        .queryParam("toRootOffenderId", toRootOffenderId)
        .build()
    }
    .httpRequest {
      it.getNativeRequest<HttpClientRequest>().responseTimeout(Duration.ofMinutes(1))
    }
    .retrieve()
    .bodyToMono(object : ParameterizedTypeReference<List<String>>() {})
    .block()!!
}
