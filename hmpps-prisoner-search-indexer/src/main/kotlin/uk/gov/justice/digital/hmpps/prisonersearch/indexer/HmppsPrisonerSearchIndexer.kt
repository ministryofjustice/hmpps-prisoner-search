package uk.gov.justice.digital.hmpps.prisonersearch.indexer

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchDataAutoConfiguration
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchClientAutoConfiguration
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchRestClientAutoConfiguration
import org.springframework.boot.autoconfigure.elasticsearch.ReactiveElasticsearchClientAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.IndexBuildProperties

@SpringBootApplication(
  exclude = [
    ElasticsearchDataAutoConfiguration::class, ElasticsearchRestClientAutoConfiguration::class,
    ReactiveElasticsearchClientAutoConfiguration::class, ElasticsearchClientAutoConfiguration::class,
  ],
  scanBasePackages = [
    "uk.gov.justice.digital.hmpps.prisonersearch",
  ],
)
@EnableConfigurationProperties(IndexBuildProperties::class)
class HmppsPrisonerSearchIndexer

fun main(args: Array<String>) {
  runApplication<HmppsPrisonerSearchIndexer>(*args)
}
