package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonersearch.common.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.dto.nomis.OffenderBooking

/**
 * This service tests the new offender endpoint in prison-api by launching a coroutine to call the new endpoint and
 * compare the results to the offender booking retrieved by the old endpoint, publishing the results to App Insights.
 */
@ConditionalOnProperty(value = ["diff.testNewOffenderEndpoint"], havingValue = "true")
@Component
class NewOffenderEndpointTestService(
  private val telemetryClient: TelemetryClient,
  private val nomisService: NomisService,
) {
  fun launchOffenderEndpointDiffTest(oldEndpointBooking: OffenderBooking) {
    CoroutineScope(SupervisorJob()).launch {
      trackDifferences(oldEndpointBooking)
    }
  }

  fun trackDifferences(oldEndpointBooking: OffenderBooking) {
    try {
      nomisService.getOffenderNewEndpoint(oldEndpointBooking.offenderNo)
        ?.also { newEndpointBooking ->
          val diff = oldEndpointBooking.diff(newEndpointBooking)
          if (diff.diffs.isNotEmpty()) {
            telemetryClient.trackEvent(
              "OffenderBookingDifference",
              mapOf(
                "offenderNo" to oldEndpointBooking.offenderNo,
                "diff" to diff.diffs.joinToString(", ") { it.fieldName },
              ),
            )
          }
        }
        ?: also {
          telemetryClient.trackEvent(
            "OffenderBookingDifference",
            mapOf("offenderNo" to oldEndpointBooking.offenderNo, "diff" to "new_booking_null"),
          )
        }
    } catch (e: Exception) {
      telemetryClient.trackEvent(
        "OffenderBookingDifference",
        mapOf("offenderNo" to oldEndpointBooking.offenderNo, "diff" to "exception", "exception" to e.message.toString()),
      )
      // rethrow as well as publishing telemetry so we don't lose any information about the exception (it should appear in App Insights - I think)
      throw e
    }
  }
}
