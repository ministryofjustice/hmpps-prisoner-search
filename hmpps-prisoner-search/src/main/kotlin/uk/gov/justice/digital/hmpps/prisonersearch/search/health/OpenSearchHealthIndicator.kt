package uk.gov.justice.digital.hmpps.prisonersearch.search.health

import org.springframework.boot.health.contributor.AbstractHealthIndicator
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.Status
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.data.elasticsearch.core.cluster.ClusterHealth
import org.springframework.stereotype.Component

@Component
class OpenSearchHealthIndicator(private val template: ElasticsearchOperations) : AbstractHealthIndicator() {
  override fun doHealthCheck(builder: Health.Builder): Unit = processResponse(builder, template.cluster().health())

  private fun processResponse(builder: Health.Builder, response: ClusterHealth) {
    if (response.isTimedOut) {
      builder.down().build()
    } else {
      val status = response.status
      builder.status(if (status == "RED") Status.OUT_OF_SERVICE else Status.UP)
      builder.withDetail("cluster_name", response.clusterName)
      builder.withDetail("status", response.status)
      builder.withDetail("timed_out", response.isTimedOut)
      builder.build()
    }
  }
}
