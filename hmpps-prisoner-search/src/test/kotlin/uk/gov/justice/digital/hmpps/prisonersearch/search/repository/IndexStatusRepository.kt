package uk.gov.justice.digital.hmpps.prisonersearch.search.repository

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IndexStatus

@Repository
interface IndexStatusRepository : ElasticsearchRepository<IndexStatus, String>
