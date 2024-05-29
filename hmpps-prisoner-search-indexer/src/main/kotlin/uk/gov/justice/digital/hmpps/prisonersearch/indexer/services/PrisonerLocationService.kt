package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import org.springframework.stereotype.Service

@Service
class PrisonerLocationService {
  fun findPrisoners(prisonId: String, cellLocation: String): List<String> = emptyList()
}
