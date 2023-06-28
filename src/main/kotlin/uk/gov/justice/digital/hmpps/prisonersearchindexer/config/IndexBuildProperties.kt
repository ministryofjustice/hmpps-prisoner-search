package uk.gov.justice.digital.hmpps.prisonersearchindexer.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "index-build")
data class IndexBuildProperties(val pageSize: Int, val completeThreshold: Long)
