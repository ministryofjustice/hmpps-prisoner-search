package uk.gov.justice.digital.hmpps.prisonersearch.search.resource

import com.atlassian.oai.validator.OpenApiInteractionValidator
import com.atlassian.oai.validator.model.Request
import com.atlassian.oai.validator.model.SimpleRequest
import com.atlassian.oai.validator.model.SimpleResponse
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat
import io.swagger.v3.parser.OpenAPIV3Parser
import net.minidev.json.JSONArray
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.prisonersearch.search.integration.IntegrationTestBase
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class OpenApiDocsTest : IntegrationTestBase() {
  @LocalServerPort
  private val port: Int = 0

  @Test
  fun `open api docs are available`() {
    webTestClient.get()
      .uri("/swagger-ui/index.html?configUrl=/v3/api-docs")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
  }

  @Test
  fun `open api docs redirect to correct page`() {
    webTestClient.get()
      .uri("/swagger-ui.html")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().is3xxRedirection
      .expectHeader().value("Location") { it.contains("/swagger-ui/index.html?configUrl=/v3/api-docs/swagger-config") }
  }

  @Test
  fun `the open api json contains documentation`() {
    webTestClient.get()
      .uri("/v3/api-docs")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("paths").isNotEmpty
  }

  @Test
  fun `the open api json contains the version number`() {
    webTestClient.get()
      .uri("/v3/api-docs")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody().jsonPath("info.version").value<String> {
        assertThat(it).startsWith(DateTimeFormatter.ISO_DATE.format(LocalDate.now()))
      }
  }

  @Test
  fun `the open api json is valid and contains documentation`() {
    val result = OpenAPIV3Parser().readLocation("http://localhost:$port/v3/api-docs", null, null)
    assertThat(result.messages).isEmpty()
    assertThat(result.openAPI.paths).isNotEmpty

    System.setProperty("bind-type", "true")

    val validator = OpenApiInteractionValidator.createFor(result.openAPI).build()
    val report = validator.validateRequest(
      SimpleRequest.Builder
        .get("/prisoner/A12345")
        .withAccept(MediaType.APPLICATION_JSON_VALUE)
        .withContentType(MediaType.APPLICATION_JSON_VALUE)
        .withAuthorization("Bearer 12345")
        .build(),
    )
    assertThat(report.messages).isEmpty()
    assertThat(report.hasErrors()).isFalse

    val response = validator.validateResponse(
      "/attribute-search",
      Request.Method.POST,
      SimpleResponse.Builder
        .ok()
        .withBody(
          """
      {
        "totalElements": 0,
        "totalPages": 0,
        "first": true,
        "last": true,
        "size": 0,
        "content": [
          {
            "prisonerNumber": "A1234AA",
            "pncNumber": "12/394773H",
            "pncNumberCanonicalShort": "12/394773H",
            "pncNumberCanonicalLong": "2012/394773H",
            "croNumber": "29906/12J",
            "bookingId": "0001200924",
            "bookNumber": "38412A",
            "title": "Ms",
            "firstName": "Robert",
            "middleNames": "John James",
            "lastName": "Larsen",
            "dateOfBirth": "1975-04-02",
            "gender": "Female",
            "ethnicity": "White: Eng./Welsh/Scot./N.Irish/British",
            "raceCode": "W1",
            "youthOffender": true,
            "maritalStatus": "Widowed",
            "religion": "Church of England (Anglican)",
            "nationality": "Egyptian",
            "smoker": "Y",
            "personalCareNeeds": [
              {
                "problemType": "MATSTAT",
                "problemCode": "ACCU9",
                "problemStatus": "ON",
                "problemDescription": "string",
                "commentText": "string",
                "startDate": "2020-06-21",
                "endDate": "2025-05-11"
              }
            ],
            "languages": [
              {
                "type": "PRIM",
                "code": "ENG",
                "readSkill": "Y",
                "writeSkill": "Y",
                "speakSkill": "Y",
                "interpreterRequested": true
              }
            ],
            "currentFacialImageId": 2122100,
            "status": "ACTIVE IN",
            "lastMovementTypeCode": "CRT",
            "lastMovementReasonCode": "CA",
            "inOutStatus": "IN",
            "prisonId": "MDI",
            "lastPrisonId": "MDI",
            "prisonName": "HMP Leeds",
            "cellLocation": "A-1-002",
            "aliases": [
              {
                "title": "Ms",
                "firstName": "Robert",
                "middleNames": "Trevor",
                "lastName": "Lorsen",
                "dateOfBirth": "1975-04-02",
                "gender": "Male",
                "ethnicity": "White : Irish",
                "raceCode": "W1"
              }
            ],
            "alerts": [
              {
                "alertType": "H",
                "alertCode": "HA",
                "active": true,
                "expired": true
              }
            ],
            "csra": "HIGH",
            "category": "C",
            "complexityOfNeedLevel": "low",
            "legalStatus": "SENTENCED",
            "imprisonmentStatus": "LIFE",
            "imprisonmentStatusDescription": "Serving Life Imprisonment",
            "convictedStatus": "Convicted",
            "mostSeriousOffence": "Robbery",
            "recall": false,
            "indeterminateSentence": true,
            "sentenceStartDate": "2020-04-03",
            "releaseDate": "2023-05-02",
            "confirmedReleaseDate": "2023-05-01",
            "sentenceExpiryDate": "2023-05-01",
            "licenceExpiryDate": "2023-05-01",
            "homeDetentionCurfewEligibilityDate": "2023-05-01",
            "homeDetentionCurfewActualDate": "2023-05-01",
            "homeDetentionCurfewEndDate": "2023-05-02",
            "topupSupervisionStartDate": "2023-04-29",
            "topupSupervisionExpiryDate": "2023-05-01",
            "additionalDaysAwarded": 10,
            "nonDtoReleaseDate": "2023-05-01",
            "nonDtoReleaseDateType": "ARD",
            "receptionDate": "2023-05-01",
            "lastAdmissionDate": "2023-05-01",
            "paroleEligibilityDate": "2023-05-01",
            "automaticReleaseDate": "2023-05-01",
            "postRecallReleaseDate": "2023-05-01",
            "conditionalReleaseDate": "2023-05-01",
            "actualParoleDate": "2023-05-01",
            "tariffDate": "2023-05-01",
            "releaseOnTemporaryLicenceDate": "2023-05-01",
            "locationDescription": "Outside - released from Leeds",
            "restrictedPatient": true,
            "supportingPrisonId": "LEI",
            "dischargedHospitalId": "HAZLWD",
            "dischargedHospitalDescription": "Hazelwood House",
            "dischargeDate": "2020-05-01",
            "dischargeDetails": "Psychiatric Hospital Discharge to Hazelwood House",
            "currentIncentive": {
              "level": {
                "code": "STD",
                "description": "Standard"
              },
              "dateTime": "2022-11-10T15:47:24Z",
              "nextReviewDate": "2022-11-10"
            },
            "heightCentimetres": 200,
            "weightKilograms": 102,
            "hairColour": "Blonde",
            "rightEyeColour": "Green",
            "leftEyeColour": "Hazel",
            "facialHair": "Clean Shaven",
            "shapeOfFace": "Round",
            "build": "Muscular",
            "shoeSize": 10,
            "tattoos": [
              {
                "bodyPart": "Head",
                "comment": "Skull and crossbones covering chest"
              }
            ],
            "scars": [
              {
                "bodyPart": "Head",
                "comment": "Skull and crossbones covering chest"
              }
            ],
            "marks": [
              {
                "bodyPart": "Head",
                "comment": "Skull and crossbones covering chest"
              }
            ],
            "addresses": [
              {
                "fullAddress": "1",
                "postalCode": "S10 1BP",
                "startDate": "2020-07-17",
                "primaryAddress": true,
                "noFixedAddress": true,
                "phoneNumbers": [
                  {
                    "type": "HOME, MOB",
                    "number": "01141234567"
                  }
                ]
              }
            ],
            "emailAddresses": [
              {
                "email": "john.smith@gmail.com"
              }
            ],
            "phoneNumbers": [
              {
                "type": "HOME, MOB",
                "number": "01141234567"
              }
            ],
            "identifiers": [
              {
                "type": "PNC, CRO, DL, NINO",
                "value": "12/394773H",
                "issuedDate": "2020-07-17",
                "issuedAuthorityText": "string",
                "createdDateTime": "2020-07-17T12:34:56.833Z"
              }
            ],
            "allConvictedOffences": [
              {
                "statuteCode": "TH68",
                "offenceCode": "TH68010",
                "offenceDescription": "Theft from a shop",
                "offenceDate": "2024-05-23",
                "latestBooking": true,
                "sentenceStartDate": "2018-03-10",
                "primarySentence": true
              }
            ]
          }
        ],
        "number": 0,
        "sort": {
          "empty": true,
          "sorted": true,
          "unsorted": true
        },
        "numberOfElements": 0,
        "pageable": {
          "offset": 0,
          "sort": {
            "empty": true,
            "sorted": true,
            "unsorted": true
          },
          "pageSize": 0,
          "pageNumber": 0,
          "paged": true,
          "unpaged": true
        },
        "empty": true
      }
          """.trimIndent(),
        )
        .withContentType(MediaType.APPLICATION_JSON_VALUE)
        .build(),
    )
    assertThat(response.messages).isEmpty()
    assertThat(response.hasErrors()).isFalse

    val report2 = validator.validateRequest(
      SimpleRequest.Builder
        .post("/attribute-search")
        .withAccept(MediaType.APPLICATION_JSON_VALUE)
        .withContentType(MediaType.APPLICATION_JSON_VALUE)
        .withBody(
          """
            {
              "joinType": "AND",
              "queries": [
                {
                  "joinType": "AND",
                  "matchers": [
                    {
                      "type": "String",
                      "attribute": "prisonId",
                      "condition": "IS",
                      "searchTerm": "MDI"
                    },
                    {
                      "type": "String",
                      "attribute": "cellLocation",
                      "condition": "IS",
                      "searchTerm": "A-1-002"
                    }
                  ]
                }
              ],
              "pagination": {
                "page": 0,
                "size": 10
              }
            }
          """.trimIndent(),
        )
        .withAuthorization("Bearer 12345")
        .build(),
    )
    assertThat(report2.messages).isEmpty()
    assertThat(report2.hasErrors()).isFalse
  }

  @Test
  fun `a security scheme is setup for a HMPPS Auth token with the ROLE_VIEW_PRISONER_DATA role`() {
    webTestClient.get()
      .uri("/v3/api-docs")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.components.securitySchemes.view-prisoner-data-role.type").isEqualTo("http")
      .jsonPath("$.components.securitySchemes.view-prisoner-data-role.scheme").isEqualTo("bearer")
      .jsonPath("$.components.securitySchemes.view-prisoner-data-role.bearerFormat").isEqualTo("JWT")
      .jsonPath("$.components.securitySchemes.view-prisoner-data-role.description").value(containsString("ROLE_VIEW_PRISONER_DATA"))
      .jsonPath("$.security[0].view-prisoner-data-role")
      .isEqualTo(JSONArray().apply { addAll(listOf("read")) })
  }

  @Test
  fun `a security scheme is setup for a HMPPS Auth token with the ROLE_PRISONER_SEARCH role`() {
    webTestClient.get()
      .uri("/v3/api-docs")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.components.securitySchemes.prisoner-search-role.type").isEqualTo("http")
      .jsonPath("$.components.securitySchemes.prisoner-search-role.scheme").isEqualTo("bearer")
      .jsonPath("$.components.securitySchemes.prisoner-search-role.bearerFormat").isEqualTo("JWT")
      .jsonPath("$.components.securitySchemes.prisoner-search-role.description").value(containsString("ROLE_PRISONER_SEARCH"))
      .jsonPath("$.security[1].prisoner-search-role")
      .isEqualTo(JSONArray().apply { addAll(listOf("read")) })
  }

  @Test
  fun `a security scheme is setup for a HMPPS Auth token with the ROLE_GLOBAL_SEARCH role`() {
    webTestClient.get()
      .uri("/v3/api-docs")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.components.securitySchemes.global-search-role.type").isEqualTo("http")
      .jsonPath("$.components.securitySchemes.global-search-role.scheme").isEqualTo("bearer")
      .jsonPath("$.components.securitySchemes.global-search-role.bearerFormat").isEqualTo("JWT")
      .jsonPath("$.components.securitySchemes.global-search-role.description").value(containsString("ROLE_GLOBAL_SEARCH"))
      .jsonPath("$.security[2].global-search-role")
      .isEqualTo(JSONArray().apply { addAll(listOf("read")) })
  }

  @Test
  fun `a security scheme is setup for a HMPPS Auth token with the ROLE_PRISONER_IN_PRISON_SEARCH role`() {
    webTestClient.get()
      .uri("/v3/api-docs")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.components.securitySchemes.prisoner-in-prison-search-role.type").isEqualTo("http")
      .jsonPath("$.components.securitySchemes.prisoner-in-prison-search-role.scheme").isEqualTo("bearer")
      .jsonPath("$.components.securitySchemes.prisoner-in-prison-search-role.bearerFormat").isEqualTo("JWT")
      .jsonPath("$.components.securitySchemes.prisoner-in-prison-search-role.description").value(containsString("ROLE_PRISONER_IN_PRISON_SEARCH"))
      .jsonPath("$.security[3].prisoner-in-prison-search-role")
      .isEqualTo(JSONArray().apply { addAll(listOf("read")) })
  }

  @Test
  fun `a security scheme is setup for a HMPPS Auth token with the PRISONER_SEARCH__PRISONER__RO role`() {
    webTestClient.get()
      .uri("/v3/api-docs")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.components.securitySchemes.prisoner-search--prisoner--ro.type").isEqualTo("http")
      .jsonPath("$.components.securitySchemes.prisoner-search--prisoner--ro.scheme").isEqualTo("bearer")
      .jsonPath("$.components.securitySchemes.prisoner-search--prisoner--ro.bearerFormat").isEqualTo("JWT")
      .jsonPath("$.components.securitySchemes.prisoner-search--prisoner--ro.description").value(containsString("PRISONER_SEARCH__PRISONER__RO"))
      .jsonPath("$.security[3].prisoner-in-prison-search-role")
      .isEqualTo(JSONArray().apply { addAll(listOf("read")) })
  }
}
