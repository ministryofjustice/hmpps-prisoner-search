package uk.gov.justice.digital.hmpps.prisonersearchindexer.model

import uk.gov.justice.digital.hmpps.prisonersearchindexer.services.dto.nomis.AssignedLivingUnit
import uk.gov.justice.digital.hmpps.prisonersearchindexer.services.dto.nomis.OffenderBooking
import java.time.LocalDate

class OffenderBookingBuilder {
  fun anOffenderBooking(bookingId: Long = 12345L) = OffenderBooking(
    offenderNo = "A1234AA",
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
