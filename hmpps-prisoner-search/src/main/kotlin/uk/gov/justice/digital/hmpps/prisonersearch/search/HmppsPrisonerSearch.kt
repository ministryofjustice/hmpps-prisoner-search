package uk.gov.justice.digital.hmpps.prisonersearch.search

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchDataAutoConfiguration
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchClientAutoConfiguration
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchRestClientAutoConfiguration
import org.springframework.boot.autoconfigure.elasticsearch.ReactiveElasticsearchClientAutoConfiguration
import org.springframework.boot.runApplication

@SpringBootApplication(
  exclude = [
    ElasticsearchDataAutoConfiguration::class, ElasticsearchRestClientAutoConfiguration::class,
    ReactiveElasticsearchClientAutoConfiguration::class, ElasticsearchClientAutoConfiguration::class,
  ],
)
class HmppsPrisonerSearch

fun main(args: Array<String>) {
  runApplication<uk.gov.justice.digital.hmpps.prisonersearch.search.HmppsPrisonerSearch>(*args)
}
