package uk.gov.justice.digital.hmpps.prisonersearch.indexer

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.data.elasticsearch.autoconfigure.DataElasticsearchAutoConfiguration
import org.springframework.boot.elasticsearch.autoconfigure.ElasticsearchClientAutoConfiguration
import org.springframework.boot.elasticsearch.autoconfigure.ElasticsearchRestClientAutoConfiguration
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.DiffProperties
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.IndexBuildProperties
import java.time.Clock

@SpringBootApplication(
  exclude = [
    DataElasticsearchAutoConfiguration::class, ElasticsearchRestClientAutoConfiguration::class,
    ElasticsearchClientAutoConfiguration::class,
  ],
  scanBasePackages = [
    "uk.gov.justice.digital.hmpps.prisonersearch",
  ],
)
@EnableConfigurationProperties(IndexBuildProperties::class, DiffProperties::class)
class HmppsPrisonerSearchIndexer {
  @Bean
  fun clock(): Clock = Clock.systemUTC()
}

fun main(args: Array<String>) {
  runApplication<HmppsPrisonerSearchIndexer>(*args)
}
