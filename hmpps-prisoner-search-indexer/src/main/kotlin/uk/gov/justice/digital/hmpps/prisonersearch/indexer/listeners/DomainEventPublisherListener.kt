package uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners

import io.awspring.cloud.sqs.annotation.SqsListener
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.annotations.WithSpan
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class DomainEventPublisherListener() {
  @SqsListener("publish", factory = "hmppsQueueContainerFactoryProxy")
  @WithSpan(value = "syscon-devs-hmpps_prisoner_search_publish_queue", kind = SpanKind.SERVER)
  fun publish(requestJson: String) {
    log.debug("Received $requestJson - TODO SNS publish")
  }

  private companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
