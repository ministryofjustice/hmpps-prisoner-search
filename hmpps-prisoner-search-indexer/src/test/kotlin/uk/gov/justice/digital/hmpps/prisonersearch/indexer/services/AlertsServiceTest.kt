package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.WebClientConfiguration
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.wiremock.AlertsApiExtension.Companion.alertsApi
import java.time.LocalDate
import java.time.LocalDateTime

@SpringAPIServiceTest
@Import(AlertsService::class, WebClientConfiguration::class)
class AlertsServiceTest {
  @Autowired
  private lateinit var alertsService: AlertsService

  @BeforeEach
  fun setUp() {
    alertsApi.stubSuccess()
  }

  @Test
  fun `will supply authentication token`() {
    alertsService.getActiveAlertsForPrisoner("A1234AA")

    alertsApi.verify(
      getRequestedFor(urlEqualTo("/prisoners/A1234AA/alerts?isActive=true&page=0&size=1000&sort=activeFrom,DESC&sort=createdAt,ASC"))
        .withHeader("Authorization", equalTo("Bearer ABCDE")),
    )
  }

  @Test
  fun `will return alert data`() {
    alertsApi.stubSuccess()

    val alerts = alertsService.getActiveAlertsForPrisoner("A1234AA")
    alerts!!.first().also { alert ->
      assertThat(alert.alertCode.code).isEqualTo("ABC")
      assertThat(alert.alertCode.alertTypeCode).isEqualTo("A")
      assertThat(alert.isActive).isTrue()
    }
  }

  @Test
  fun `alert data is sorted`() {
    alertsApi.stubSuccess(
      alertCodes = listOf(
        "A" to "AAA",
        "B" to "BBB",
        "C" to "CCC",
        "D" to "DDD",
        "E" to "EEE",
        "F" to "FFF",
        "G" to "GGG",
      ),
      createdDates = listOf(
        "2023-11-01T00:00:00",
        "2023-01-03T00:00:00",
        "2023-01-02T00:00:00",
        "2023-01-01T00:00:00",
        "2023-01-03T00:00:00",
        "2023-01-02T00:00:00",
        "2023-01-01T00:00:00",
      ),
      activeFromDates = listOf(
        "2023-02-10",
        "2023-02-11",
        "2023-02-11",
        "2023-02-11",
        "2023-02-12",
        "2023-02-12",
        "2023-02-12",
      ),
    )

    val alerts = alertsService.getActiveAlertsForPrisoner("A1234AA")
    assertThat(alerts!!).extracting("alertCode.code", "alertCode.alertTypeCode", "createdAt", "activeFrom")
      .containsExactly(
        tuple("GGG", "G", LocalDateTime.parse("2023-01-01T00:00:00"), LocalDate.parse("2023-02-12")),
        tuple("FFF", "F", LocalDateTime.parse("2023-01-02T00:00:00"), LocalDate.parse("2023-02-12")),
        tuple("EEE", "E", LocalDateTime.parse("2023-01-03T00:00:00"), LocalDate.parse("2023-02-12")),
        tuple("DDD", "D", LocalDateTime.parse("2023-01-01T00:00:00"), LocalDate.parse("2023-02-11")),
        tuple("CCC", "C", LocalDateTime.parse("2023-01-02T00:00:00"), LocalDate.parse("2023-02-11")),
        tuple("BBB", "B", LocalDateTime.parse("2023-01-03T00:00:00"), LocalDate.parse("2023-02-11")),
        tuple("AAA", "A", LocalDateTime.parse("2023-11-01T00:00:00"), LocalDate.parse("2023-02-10")),
      )
  }
}
