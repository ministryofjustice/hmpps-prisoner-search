package uk.gov.justice.digital.hmpps.prisonersearch.indexer.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "diff")
data class DiffProperties(val events: Boolean, val host: String, val prefix: String)
