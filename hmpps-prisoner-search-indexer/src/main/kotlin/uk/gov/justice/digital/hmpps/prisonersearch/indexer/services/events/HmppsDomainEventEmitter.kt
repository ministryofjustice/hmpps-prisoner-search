package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.DiffCategory
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.Difference

@Service
class HmppsDomainEventEmitter {
  fun emitPrisonerDifferenceEvent(offenderNo: String, it: Map<DiffCategory, List<Difference>>) {
    TODO("Not yet implemented")
  }

  fun emitPrisonerCreatedEvent(offenderNo: String): Prisoner {
    TODO("Not yet implemented")
  }
}
