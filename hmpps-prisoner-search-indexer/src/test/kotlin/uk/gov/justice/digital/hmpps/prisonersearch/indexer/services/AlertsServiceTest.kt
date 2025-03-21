package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.WebClientConfiguration
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.wiremock.AlertsApiExtension.Companion.alertsApi

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
      getRequestedFor(urlEqualTo("/prisoners/A1234AA/alerts?isActive=true&page=0&size=1000"))
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
}
