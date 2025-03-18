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

  fun stubSuccess() {
    stubFor(
      get(urlPathMatching("/prisoners/.+/alerts"))
        .willReturn(
          okJson(
            """
            {
              "totalPages": 1,
              "totalElements": 1,
              "first": true,
              "last": true,
              "size": 1,
              "content": [
                {
                  "alertUuid": "8cdadcf3-b003-4116-9956-c99bd8df6a00",
                  "prisonNumber": "A1234AA",
                  "alertCode": {
                    "alertTypeCode": "A",
                    "alertTypeDescription": "Alert type description",
                    "code": "ABC",
                    "description": "Alert code description"
                  },
                  "description": "Alert description",
                  "authorisedBy": "A. Nurse, An Agency",
                  "activeFrom": "2021-09-27",
                  "activeTo": "2022-07-15",
                  "isActive": true,
                  "createdAt": "2021-09-27T14:19:25",
                  "createdBy": "USER1234",
                  "createdByDisplayName": "Firstname Lastname",
                  "lastModifiedAt": "2022-07-15T15:24:56",
                  "lastModifiedBy": "USER1234",
                  "lastModifiedByDisplayName": "Firstname Lastname",
                  "activeToLastSetAt": "2022-07-15T15:24:56",
                  "activeToLastSetBy": "USER1234",
                  "activeToLastSetByDisplayName": "Firstname Lastname",
                  "prisonCodeWhenCreated": "LEI"
                }
              ],
              "number": 0,
              "sort": {
                "empty": true,
                "sorted": true,
                "unsorted": true
              },
              "numberOfElements": 1,
              "pageable": {
                "offset": 9007199254740991,
                "sort": {
                  "empty": true,
                  "sorted": true,
                  "unsorted": true
                },
                "unpaged": true,
                "pageSize": 1073741824,
                "paged": true,
                "pageNumber": 1073741824
              },
              "empty": true
            }
            """.trimIndent(),
          ),
        ),
    )
  }

  fun stubNotFound() {
    stubFor(
      get(urlPathMatching("/prisoners/.+/alerts"))
        .willReturn(
          aResponse()
            .withStatus(404),
        ),
    )
  }

  fun stubErrorFollowedByNotFound() {
    // Stub error followed by not found
  }

  fun stubError() {
    // Stub error
  }
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
