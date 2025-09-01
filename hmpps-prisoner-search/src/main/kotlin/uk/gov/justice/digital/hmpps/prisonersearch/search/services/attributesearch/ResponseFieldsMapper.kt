package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.exceptions.BadRequestException

@Service
class ResponseFieldsMapper {
  fun translate(responseFields: List<String>?, responseFieldsClient: String?): List<String>? {
    // break out - if no client specified just use response fields
    if (responseFieldsClient.isNullOrBlank()) return responseFields

    // otherwise grab the fields from the client and join with any extra response fields specified
    return (
      (
        clientMap[responseFieldsClient]
          ?: throw BadRequestException("Invalid response fields client requested: $responseFieldsClient")
        ) +
        (responseFields ?: emptyList())
      )
      .toSet()
      .toList()
      .ifEmpty { null }
  }

  companion object {
    val clientMap = mapOf(
      "restricted-patients" to listOf(
        "alerts",
        "category",
        "cellLocation",
        "conditionalReleaseDate",
        "dischargedHospitalDescription",
        "firstName",
        "indeterminateSentence",
        "lastMovementReasonCode",
        "lastMovementTypeCode",
        "lastName",
        "locationDescription",
        "prisonerNumber",
        "recall",
        "restrictedPatient",
        "sentenceExpiryDate",
        "supportingPrisonId",
      ),
    )
  }
}
