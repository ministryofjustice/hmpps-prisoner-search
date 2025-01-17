package uk.gov.justice.digital.hmpps.prisonersearch.search.resource

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY
import com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.PrisonerLocationService

@RestController
@Validated
@PreAuthorize("hasAnyRole('PRISONER_SEARCH__PRISONER_LOCATION__RO')")
@Tag(name = "Specific use case")
@RequestMapping(
  "/prisoner-location",
  produces = [MediaType.APPLICATION_JSON_VALUE],
)
class AllPrisonerLocationResource(private val prisonerLocationService: PrisonerLocationService) {
  @GetMapping("/all")
  @Operation(
    summary = "Get all prisoners.  This will return the first page of results and a scroll id to retrieve the next page",
    description = """To be used in conjunction with /prisoner-location/scroll/{scroll-id}.
      This endpoint will return the first page of results (10,000 per page) and also a scroll id.
      This scroll id can then be used to make subsequent calls to return the data.
      It is required that this call will then be followed by calls to the /scroll/{scroll-id} endpoint to retrieve all
      the rest of the data, since it opens an OpenSearch scroll that will be cleared when no more data is available.
      Requires PRISONER_SEARCH__PRISONER_LOCATION__RO role.""",
  )
  fun findAll() = prisonerLocationService.getAllPrisonerLocations()

  @GetMapping("/scroll/{scroll-id}")
  @Operation(
    summary = "Get the next page of prisoners.",
    description = """To be used in conjunction with /prisoner-location/all. Uses the scroll id to get the next page of
      prisoners.  Calls should be repeated until a
      `null` scroll id is then returned, indicating that no more data is available.  Each call will return the scroll id,
      which should be used for the next call, rather than re-using the original scroll id.
      Note that the OpenSearch scroll will expire after 5 minutes, so if no calls are made during that time then the
      scroll id will be invalid.
      Requires PRISONER_SEARCH__PRISONER_LOCATION__RO role.""",
  )
  fun scroll(@PathVariable("scroll-id") scrollId: String) =
    prisonerLocationService.scrollPrisonerLocations(scrollId)
}

@Schema(description = "Prisoner location response")
@JsonInclude(NON_EMPTY)
data class PrisonerLocationResponse(
  @Schema(description = "Scroll id. To be kept and used in next request", example = "FGluY2x1ZGVfY29udGV4dF91dWlkDnF1ZXJ5VGhlbkZldG...")
  val scrollId: String?,
  @Schema(description = "List of prisoner locations")
  val locations: List<PrisonerLocation>?,
)

// These three fields need to match the fetchSource fields in PrisonerLocationService that are used to restrict the
// fields returned from OpenSearch
@Schema(description = "Prisoner location")
@JsonInclude(NON_NULL)
data class PrisonerLocation(
  @Schema(description = "Prisoner number", example = "A1234AA")
  val prisonerNumber: String,
  @Schema(description = "Prison id. Current prison or OUT if outside. Will not be returned if no bookings.", example = "MDI")
  val prisonId: String?,
  @Schema(description = "Last prison id. If prisonId is OUT then will contain last prison, otherwise will be the same as prisonId. Will not be returned if no bookings.", example = "MDI")
  val lastPrisonId: String?,
)
