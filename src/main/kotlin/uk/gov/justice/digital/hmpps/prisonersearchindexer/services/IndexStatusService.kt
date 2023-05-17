package uk.gov.justice.digital.hmpps.prisonersearchindexer.services

import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearchindexer.model.IndexStatus
import uk.gov.justice.digital.hmpps.prisonersearchindexer.model.SyncIndex
import uk.gov.justice.digital.hmpps.prisonersearchindexer.repository.IndexStatusRepository

@Service
class IndexStatusService(
  private val indexStatusRepository: IndexStatusRepository,
) {

  fun getCurrentIndex(): IndexStatus = indexStatusRepository.findByIdOrNull("STATUS")
    ?: indexStatusRepository.save(IndexStatus("STATUS", SyncIndex.INDEX_A, null, null, false))
}
