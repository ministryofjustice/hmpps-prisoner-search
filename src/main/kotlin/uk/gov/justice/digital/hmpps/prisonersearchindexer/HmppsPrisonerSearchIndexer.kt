package uk.gov.justice.digital.hmpps.prisonersearchindexer

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchDataAutoConfiguration
import org.springframework.boot.runApplication

@SpringBootApplication(exclude = [ElasticsearchDataAutoConfiguration::class])
class HmppsPrisonerSearchIndexer

fun main(args: Array<String>) {
  runApplication<HmppsPrisonerSearchIndexer>(*args)
}
