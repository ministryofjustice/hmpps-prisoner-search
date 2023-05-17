package uk.gov.justice.digital.hmpps.prisonersearchindexer.repository

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.prisonersearchindexer.model.IndexStatus

@Repository
interface IndexStatusRepository : ElasticsearchRepository<IndexStatus, String>
