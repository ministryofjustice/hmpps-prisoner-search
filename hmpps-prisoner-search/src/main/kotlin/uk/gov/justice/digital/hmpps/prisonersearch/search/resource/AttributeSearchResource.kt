package uk.gov.justice.digital.hmpps.prisonersearch.search.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springdoc.core.annotations.ParameterObject
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonersearch.search.resource.advice.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.AttributeSearchService
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.AttributeSearchRequest

@RestController
@Validated
@PreAuthorize("hasAnyRole('ROLE_GLOBAL_SEARCH', 'ROLE_PRISONER_SEARCH')")
@RequestMapping(
  "/attribute-search",
  produces = [MediaType.APPLICATION_JSON_VALUE],
  consumes = [MediaType.APPLICATION_JSON_VALUE],
)
class AttributeSearchResource(private val attributeSearchService: AttributeSearchService) {

  @PostMapping
  @Tag(name = "Attribute search")
  @Operation(
    summary = "WIP - DO NOT USE!!! Search for prisoners by attributes",
    description = """This endpoint allows you to create queries over all attributes from the [Prisoner] record. Requires ROLE_GLOBAL_SEARCH or ROLE_PRISONER_SEARCH role.
      
      The request contains a query to search on one or more attributes using a list of matchers. For example attribute "lastName""
      requires a [StringMatcher] so we can query on "lastName IS Smith". Other type matchers include [IntMatcher], [BooleanMatcher],
      [DateMatcher] and [DateTimeMatcher]. We have the facility to easily create additional matchers as required, for example 
      we may end up with a PNCMatcher that handles any format of PNC number.
      
      Each query can also contain a list of sub-queries. Each sub-query can be considered as a separate query in brackets.
      Combining multiple sub-queries gives us the ability to create complex searches using any combination of a prisoner's 
      attributes. For example we can model queries such as "lastName IS Smith AND (prisonId IS MDI OR prisonId IS LEI)".
      
      To find all attributes that can be searched for please refer to the [Prisoner] record. Attributes from lists can be
      searched for with dot notation, e.g. "attribute=aliases.firstName" or "attribute=tattoos.bodyPart". Attributes from
      complex objects can also be searched for with dot notation, e.g. "attribute=currentIncentive.level.code".
      
      Example Requests:
      
      Search for all prisoners in Moorland with a height between 150 and 180cm
      
      Query: "prisonId IS "MDI" AND (heightCentimetres BETWEEN 150 AND 180)"
      
      JSON request:
      {
        "queries": [
          {
            "joinType"" "AND",
            "matchers": [
              {
                "type": "String",
                "attribute": "prisonId",
                "condition": "IS",
                "searchTerm": "MDI"
              },
              {
                "type": "Int",
                "attribute": "heightCentimetres",
                "minValue": 150,
                "maxValue": 180
              }
            ],
          }
        ]
      }
      
      
      Search for all prisoners received since 1st Jan 2024 with a dragon tattoo on either their arm or shoulder
      
      Query: "receptionDate >= 2024-01-01 AND ((tattoos.bodyPart IS "arm" AND tattoos.comment CONTAINS "dragon" ) OR (tattoos.bodyPart IS "shoulder" AND tattoos.comment CONTAINS "dragon"))"
      
      JSON request:
      {
        "queries": [
          {
            "joinType"" "AND",
            "matchers": [
              {
                "type": "Date",
                "attribute": "receptionDate",
                "minValue": "2024-01-01"
              }
            [,
            "subQueries": [
              {
                "joinType"" "OR",
                "subQueries": [
                  {
                    "joinType"" "AND",
                    "matchers": [
                      {
                        "type": "String",
                        "attribute": "tattoos.bodyPart",
                        "condition": "IS",
                        "searchTerm": "arm"
                      },
                      {
                        "type": "String",
                        "attribute": "tattoos.comment",
                        "condition": "CONTAINS",
                        "searchTerm": "dragon"
                      }
                    ]
                  },
                  {
                    "joinType"" "AND",
                    "matchers": [
                      {
                        "type": "String",
                        "attribute": "tattoos.bodyPart",
                        "condition": "IS",
                        "searchTerm": "shoulder"
                      },
                      {
                        "type": "String",
                        "attribute": "tattoos.comment",
                        "condition": "CONTAINS",
                        "searchTerm": "dragon"
                      }
                    ]
                  }
                ]    
              }
            ]
          }
        ]
      }
      
    """,
    security = [SecurityRequirement(name = "ROLE_GLOBAL_SEARCH"), SecurityRequirement(name = "ROLE_PRISONER_SEARCH")],
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Search successfully performed",
      ),
      ApiResponse(
        responseCode = "400",
        description = "Incorrect information provided to perform an attribute search",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun attributeSearch(
    @Parameter(required = true) @RequestBody request: AttributeSearchRequest,
    @ParameterObject @PageableDefault pageable: Pageable,
  ) =
    attributeSearchService.search(request, pageable)
}
