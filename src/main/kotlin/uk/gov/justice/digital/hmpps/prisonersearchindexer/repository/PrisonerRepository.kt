package uk.gov.justice.digital.hmpps.prisonersearchindexer.repository

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository
import org.springframework.data.repository.NoRepositoryBean
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.prisonersearchindexer.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearchindexer.model.PrisonerA
import uk.gov.justice.digital.hmpps.prisonersearchindexer.model.PrisonerB

@NoRepositoryBean
interface PrisonerRepository<T : Prisoner, String> : ElasticsearchRepository<T, String>

@Repository
interface PrisonerARepository : PrisonerRepository<PrisonerA, String>

@Repository
interface PrisonerBRepository : PrisonerRepository<PrisonerB, String>
