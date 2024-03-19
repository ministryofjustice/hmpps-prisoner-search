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
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonersearch.search.resource.advice.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.AttributeSearchService
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.AttributeSearchRequest

typealias AttributeTypes = Map<String, String>

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
    summary = "*** WIP - DO NOT USE!!! *** Search for prisoners by attributes",
    description = """<p>This endpoint allows you to create queries over all attributes from the <em>Prisoner</em> record. Requires ROLE_GLOBAL_SEARCH or ROLE_PRISONER_SEARCH role.</p>
      <p>The request contains a list of queries to search on one or more attributes using a list of matchers. For example attribute "lastName""
      requires a <em>StringMatcher</em> so we can query on <strong>"lastName IS Smith"</strong>. Other type matchers include <em>IntMatcher</em>, <em>BooleanMatcher</em>,
      <em>DateMatcher</em> and <em>DateTimeMatcher</em>. We have the facility to easily create additional matchers as required, for example 
      we may end up with a PNCMatcher that handles any format of PNC number.
      </p>
      <p>Each query can also contain a list of sub-queries. Each sub-query can be considered as a separate query in brackets.
      Combining multiple sub-queries gives us the ability to create complex searches using any combination of a prisoner's 
      attributes. For example we can model nested queries such as <strong>"lastName IS Smith AND (prisonId IS MDI OR (prisonId IS OUT AND inOutStatus is OUT))"</strong>.
      </p>
      <p>Please be aware that when searching lists of complex objects (e.g. aliases, alerts, tattoos) if you want to search for multiple attributes within the same object then you need 
      to include them in the same query. For example, to search for alias "John Smith" you should search for aliases.firstName IS "John" and aliases.lastName IS "Smith" in a single query.
      If you search for them in different queries you will receive anyone with firstName John and also anyone with lastName Smith.
      </p>
      <p>To find all attributes that can be searched for please refer to the <em>Prisoner</em> record or get them from endpoint <string>GET /attribute-search/attributes</strong>. Attributes from lists can be
      searched for with dot notation, e.g. <strong>"attribute=aliases.firstName"</strong> or <strong>"attribute=tattoos.bodyPart"</strong>. 
      Attributes from complex objects can also be searched for with dot notation, e.g. <strong>"attribute=currentIncentive.level.code"</strong>.
      </p>
      <p>To assist with debugging queries we publish events in App Insights. To search in App Insights Log Analytics run query: 
      <pre>
        AppEvents
        | where Name == 'POSAttributeSearch'
      </pre>
      </p>
      <h3>Example Requests</h3>
      <p>Note that the default "joinType" is "AND" so it could be omitted from the examples below (but is included for clarity).</p>
      <h4>Search for all prisoners in Moorland with a height between 150 and 180cm</h4>
      <br/>
      Query: <strong>"prisonId = "MDI" AND (heightCentimetres >= 150 AND heightCentimetres <= 180)"</strong>
      <br/>
      JSON request:
      <br/>
      <pre>
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
                  "type": "Int",
                  "attribute": "heightCentimetres",
                  "minValue": 150,
                  "maxValue": 180
                }
              ]
            }
          ]
        }
      </pre>
      <br/>
      <h4>Search for all prisoners received since 1st Jan 2024 with a dragon tattoo on either their arm or shoulder</h4>
      <br/>
      Query: <strong>"(receptionDate >= 2024-01-01) AND ((tattoos.bodyPart = "arm" AND tattoos.comment CONTAINS "dragon" ) OR (tattoos.bodyPart = "shoulder" AND tattoos.comment CONTAINS "dragon"))"</strong>
      <br/>
      JSON request:
      <br/>
      <pre>
        {
          "joinType": "AND",
          "queries": [
            {
              "matchers": [
                {
                  "type": "Date",
                  "attribute": "receptionDate",
                  "minValue": "2024-01-01"
                }
              ]
            },
            {
              "joinType": "OR",
              "subQueries": [
                {
                  "joinType": "AND",
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
                  "joinType": "AND",
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
      </pre>
    """,
    security = [SecurityRequirement(name = "global-search-role"), SecurityRequirement(name = "prisoner-search-role")],
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

  @GetMapping("/attributes")
  @Tag(name = "Attribute search")
  @Operation(
    summary = "*** WIP - DO NOT USE!!! *** Retrieve all attributes supported by the attribute search",
    description = "Returns all attributes that can be passed into the attribute search including their type.",
    security = [SecurityRequirement(name = "global-search-role"), SecurityRequirement(name = "prisoner-search-role")],
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Search successfully performed",
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
  fun getAttributes(): AttributeTypes = attributeSearchService.getAttributes()
}
