package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.dto.nomis.OffenderBooking

@Service
class PrisonerMovementsEventService {
  fun generateAnyEvents(previousPrisonerSnapshot: Prisoner?, prisoner: Prisoner, offenderBooking: OffenderBooking) {
    TODO("Not yet implemented")
  }
}
