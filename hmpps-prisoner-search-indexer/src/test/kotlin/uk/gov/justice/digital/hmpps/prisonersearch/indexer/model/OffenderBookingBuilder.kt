package uk.gov.justice.digital.hmpps.prisonersearch.indexer.model

import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.dto.nomis.AssignedLivingUnit
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.dto.nomis.OffenderBooking
import java.time.LocalDate

class OffenderBookingBuilder {
  fun anOffenderBooking(bookingId: Long? = 12345L, offenderNo: String = "A1234AA") = OffenderBooking(
    offenderNo = offenderNo,
    firstName = "Fred",
    lastName = "Bloggs",
    dateOfBirth = LocalDate.of(1976, 5, 15),
    bookingId = bookingId,
    assignedLivingUnit = AssignedLivingUnit(
      agencyId = "MDI",
      locationId = 1,
      description = "Moorland",
      agencyName = "Moorland",
    ),
    activeFlag = true,
  )
}
