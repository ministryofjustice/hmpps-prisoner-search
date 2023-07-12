package uk.gov.justice.digital.hmpps.prisonersearch.common.config

import org.springframework.context.annotation.Configuration

@Configuration
class OpenSearchIndexConfiguration {

  companion object {
    const val INDEX_ALIAS = "prisoner"
  }
}
