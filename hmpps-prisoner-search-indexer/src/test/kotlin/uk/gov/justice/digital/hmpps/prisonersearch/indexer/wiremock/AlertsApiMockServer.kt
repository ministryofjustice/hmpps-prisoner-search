package uk.gov.justice.digital.hmpps.prisonersearch.indexer.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import java.util.UUID

class AlertsApiMockServer : WireMockServer(8097) {
  fun stubHealthPing(status: Int) {
    stubFor(
      get("/health/ping").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(if (status == 200) "pong" else "some error")
          .withStatus(status),
      ),
    )
  }

  fun stubSuccess(
    prisonerNumber: String = "A1234AA",
    alertCodes: List<Pair<String, String>> = listOf("A" to "ABC"),
    createdDates: List<String> = emptyList(),
    activeFromDates: List<String> = emptyList(),
  ) {
    val alerts = alertCodes.mapIndexed { index, (type, code) ->
      alertJson(prisonerNumber, type, code, createdDates.getOrNull(index), activeFromDates.getOrNull(index))
    }
      .joinToString(",")

    stubFor(
      get(urlPathMatching("/prisoners/$prisonerNumber/alerts"))
        .willReturn(
          okJson(resultJson(alertCodes.size, alerts)),
        ),
    )
  }

  private fun alertJson(prisonerNumber: String, type: String, code: String, createdAt: String?, activeFrom: String?): String = """{
        "alertUuid": "${UUID.randomUUID()}",
        "prisonNumber": "$prisonerNumber",
        "alertCode": {
          "alertTypeCode": "$type",
          "alertTypeDescription": "Type Description for $type",
          "code": "$code",
          "description": "Code description for $code"
        },
        "description": "Alert description",
        "authorisedBy": "A. Nurse, An Agency",
        "activeFrom": "${activeFrom ?: "2022-07-15"}",
        "activeTo": "2022-07-15",
        "isActive": true,
        "createdAt": "${createdAt ?: "2021-09-27T14:19:25"}",
        "createdBy": "USER1234",
        "createdByDisplayName": "Firstname Lastname",
        "lastModifiedAt": "2022-07-15T15:24:56",
        "lastModifiedBy": "USER1234",
        "lastModifiedByDisplayName": "Firstname Lastname",
        "activeToLastSetAt": "2022-07-15T15:24:56",
        "activeToLastSetBy": "USER1234",
        "activeToLastSetByDisplayName": "Firstname Lastname",
        "prisonCodeWhenCreated": "LEI"
      }"""

  private fun resultJson(
    size: Int,
    alerts: String,
  ): String = """
    {
      "totalPages": 1,
      "totalElements": $size,
      "first": true,
      "last": true,
      "size": $size,
      "content": [ $alerts ],
      "number": 0,
      "sort": {
        "empty": true,
        "sorted": true,
        "unsorted": true
      },
      "numberOfElements": $size,
      "pageable": {
        "offset": 0,
        "sort": {
          "empty": false,
          "sorted": false,
          "unsorted": true
        },
        "unpaged": false,
        "pageSize": 1000,
        "paged": true,
        "pageNumber": 0
      },
      "empty": false
    }
  """.trimIndent()
}

class AlertsApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val alertsApi = AlertsApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext): Unit = alertsApi.start()
  override fun beforeEach(context: ExtensionContext): Unit = alertsApi.resetAll()
  override fun afterAll(context: ExtensionContext): Unit = alertsApi.stop()
}
