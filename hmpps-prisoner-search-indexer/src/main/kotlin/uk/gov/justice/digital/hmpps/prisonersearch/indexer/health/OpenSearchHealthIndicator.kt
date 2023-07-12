package uk.gov.justice.digital.hmpps.prisonersearch.indexer.health

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
      builder.withDetail("number_of_nodes", response.numberOfNodes)
      builder.withDetail("number_of_data_nodes", response.numberOfDataNodes)
      builder.withDetail("active_primary_shards", response.activePrimaryShards)
      builder.withDetail("active_shards", response.activeShards)
      builder.withDetail("relocating_shards", response.relocatingShards)
      builder.withDetail("initializing_shards", response.initializingShards)
      builder.withDetail("unassigned_shards", response.unassignedShards)
      builder.withDetail("delayed_unassigned_shards", response.delayedUnassignedShards)
      builder.withDetail("number_of_pending_tasks", response.numberOfPendingTasks)
      builder.withDetail("number_of_in_flight_fetch", response.numberOfInFlightFetch)
      builder.withDetail("task_max_waiting_in_queue_millis", response.taskMaxWaitingTimeMillis)
      builder.withDetail("active_shards_percent_as_number", response.activeShardsPercent)
      builder.build()
    }
  }
}
