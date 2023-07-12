package uk.gov.justice.digital.hmpps.prisonersearch.search.health

import org.opensearch.data.client.orhlc.OpenSearchRestTemplate
import org.springframework.boot.actuate.health.AbstractHealthIndicator
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.Status
import org.springframework.data.elasticsearch.core.cluster.ClusterHealth
import org.springframework.stereotype.Component

@Suppress("SpringJavaInjectionPointsAutowiringInspection")
@Component
class OpenSearchHealthIndicator(private val template: OpenSearchRestTemplate) : AbstractHealthIndicator() {
  override fun doHealthCheck(builder: Health.Builder): Unit =
    processResponse(builder, template.cluster().health())

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
