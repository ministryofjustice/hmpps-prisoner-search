package uk.gov.justice.digital.hmpps.prisonersearch.indexer.model

import uk.gov.justice.digital.hmpps.prisonersearch.indexer.model.nomis.OffenderBooking
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.prisonapi.model.AssignedLivingUnit
import java.time.LocalDate
import kotlin.random.Random

class OffenderBookingBuilder {
  fun anOffenderBooking(bookingId: Long? = 12345L, offenderNo: String = "A1234AA") = OffenderBooking(
    offenderId = Random.nextLong(),
    offenderNo = offenderNo,
    firstName = "Fred",
    lastName = "Bloggs",
    dateOfBirth = LocalDate.of(1976, 5, 15),
    bookingId = bookingId,
    agencyId = "MDI",
    assignedLivingUnit = AssignedLivingUnit(
      agencyId = "MDI",
      locationId = 1,
      description = "Moorland",
      agencyName = "Moorland",
    ),
  )
}
