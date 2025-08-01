package uk.gov.justice.digital.hmpps.prisonersearch.search.services

import com.fasterxml.jackson.annotation.JsonIgnore
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size

sealed class PrisonerListCriteria<out T> {
  @Schema(hidden = true)
  abstract fun values(): List<T>

  @JsonIgnore
  @Schema(hidden = true)
  val type = this::class.simpleName!!

  data class PrisonerNumbers(
    @Schema(description = "List of prisoner numbers to search by", example = "[\"A1234AA\"]")
    @NotEmpty
    @Size(min = 1, max = 1000)
    val prisonerNumbers: List<String>,
  ) : PrisonerListCriteria<String>() {

    override fun values() = prisonerNumbers
  }

  data class BookingIds(
    @Schema(description = "List of bookingIds to search by", example = "[1, 2, 3]")
    @NotEmpty
    @Size(min = 1, max = 1000)
    val bookingIds: List<Long>,
  ) : PrisonerListCriteria<Long>() {

    override fun values() = bookingIds
  }
}
