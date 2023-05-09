package uk.gov.justice.digital.hmpps.prisonersearchindexer

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication()
class HmppsPrisonerSearchIndexer

fun main(args: Array<String>) {
  runApplication<HmppsPrisonerSearchIndexer>(*args)
}
