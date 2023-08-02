package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner

@Service
class AlertsUpdatedEventService {
  fun generateAnyEvents(previousPrisonerSnapshot: Prisoner?, prisoner: Prisoner) {
    // TODO("Not yet implemented")
  }
}
