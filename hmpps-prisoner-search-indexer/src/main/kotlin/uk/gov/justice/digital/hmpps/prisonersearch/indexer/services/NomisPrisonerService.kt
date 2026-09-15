package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClientRequest
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.nomisprisoner.api.PrisonerSearchResourceApi
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.nomisprisoner.model.IdRange
import java.time.Duration

@Service
class NomisPrisonerService(
  nomisApiWebClient: WebClient,
) {
  private val prisonerSearchResourceApi = PrisonerSearchResourceApi(nomisApiWebClient)

  fun getAllPrisonersIdRanges(active: Boolean, size: Int = 10) = prisonerSearchResourceApi.prepare(
    prisonerSearchResourceApi.getAllPrisonersIdRangesForSearchRequestConfig(active = active, size = size),
  )
    .httpRequest {
      it.getNativeRequest<HttpClientRequest>().responseTimeout(Duration.ofMinutes(1))
    }
    .retrieve()
    .bodyToMono(object : ParameterizedTypeReference<List<IdRange>>() {})
    .block()!!

  fun getPrisonNumbers(active: Boolean, fromRootOffenderId: Long, toRootOffenderId: Long) = prisonerSearchResourceApi.prepare(
    prisonerSearchResourceApi.getAllPrisonersInRangeForSearchRequestConfig(active = active, fromId = fromRootOffenderId, toId = toRootOffenderId),
  )
    .httpRequest {
      it.getNativeRequest<HttpClientRequest>().responseTimeout(Duration.ofMinutes(1))
    }
    .retrieve()
    .bodyToMono(object : ParameterizedTypeReference<List<String>>() {})
    .block()!!
}
