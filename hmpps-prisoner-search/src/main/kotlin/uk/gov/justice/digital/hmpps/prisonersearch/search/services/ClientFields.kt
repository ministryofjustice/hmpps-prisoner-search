package uk.gov.justice.digital.hmpps.prisonersearch.search.services

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("search")
data class ClientFields(
  val clients: Map<String, List<String>>,
)
