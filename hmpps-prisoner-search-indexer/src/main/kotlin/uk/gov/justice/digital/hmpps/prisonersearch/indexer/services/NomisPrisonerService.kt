package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClientRequest
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.nomisprisoner.api.PrisonerSearchResourceApi
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.nomisprisoner.model.RootOffenderIdRange
import java.time.Duration

@Service
class NomisPrisonerService(
  nomisApiWebClient: WebClient,
) {
  private val prisonerSearchResourceApi = PrisonerSearchResourceApi(nomisApiWebClient)

  fun getAllPrisonersIdRanges(active: Boolean, size: Int = 10) = prisonerSearchResourceApi.prepare(
    prisonerSearchResourceApi.getAllPrisonersIdRangesRequestConfig(active = active, size = size),
  )
    .httpRequest {
      it.getNativeRequest<HttpClientRequest>().responseTimeout(Duration.ofMinutes(1))
    }
    .retrieve()
    .bodyToMono(object : ParameterizedTypeReference<List<RootOffenderIdRange>>() {})
    .block()!!

  fun getPrisonNumbers(active: Boolean, fromRootOffenderId: Long, toRootOffenderId: Long) = prisonerSearchResourceApi.prepare(
    prisonerSearchResourceApi.getAllPrisonersInRangeRequestConfig(active = active, fromRootOffenderId = fromRootOffenderId, toRootOffenderId = toRootOffenderId),
  )
    .httpRequest {
      it.getNativeRequest<HttpClientRequest>().responseTimeout(Duration.ofMinutes(1))
    }
    .retrieve()
    .bodyToMono(object : ParameterizedTypeReference<List<String>>() {})
    .block()!!
}
