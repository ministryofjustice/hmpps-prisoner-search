package uk.gov.justice.digital.hmpps.prisonersearch.search.resource

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY
import io.swagger.v3.oas.annotations.Operation
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
@RequestMapping(
  "/prisoner-location",
  produces = [MediaType.APPLICATION_JSON_VALUE],
)
class AllPrisonerLocationResource(private val prisonerLocationService: PrisonerLocationService) {
  @GetMapping("/all")
  @Operation(
    summary = "Get all prisoners",
    description = "Requires PRISONER_SEARCH__PRISONER_LOCATION__RO role",
  )
  fun findAll() = prisonerLocationService.getAllPrisonerLocations()

  @GetMapping("/scroll/{scroll-id}")
  @Operation(
    summary = "Get all prisoners",
    description = "Requires PRISONER_SEARCH__PRISONER_LOCATION__RO role",
  )
  fun scroll(@PathVariable("scroll-id") scrollId: String) =
    prisonerLocationService.scrollPrisonerLocations(scrollId)
}

@JsonInclude(NON_EMPTY)
data class PrisonerLocationResponse(
  val scrollId: String?,
  val locations: List<PrisonerLocation>?,
)

data class PrisonerLocation(
  val prisonerNumber: String,
  val prisonId: String?,
  val lastPrisonId: String?,
)
