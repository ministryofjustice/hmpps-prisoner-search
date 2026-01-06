package uk.gov.justice.digital.hmpps.prisonersearch.search

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.data.elasticsearch.autoconfigure.DataElasticsearchAutoConfiguration
import org.springframework.boot.elasticsearch.autoconfigure.ElasticsearchClientAutoConfiguration
import org.springframework.boot.elasticsearch.autoconfigure.ElasticsearchRestClientAutoConfiguration
import org.springframework.boot.runApplication

@SpringBootApplication(
  exclude = [
    DataElasticsearchAutoConfiguration::class, ElasticsearchRestClientAutoConfiguration::class,
    ElasticsearchClientAutoConfiguration::class,
  ],
  scanBasePackages = [
    "uk.gov.justice.digital.hmpps.prisonersearch",
  ],
)
class HmppsPrisonerSearch

fun main(args: Array<String>) {
  runApplication<HmppsPrisonerSearch>(*args)
}
