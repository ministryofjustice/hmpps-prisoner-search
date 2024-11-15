package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.repository.PrisonerDifferences
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.repository.PrisonerDifferencesLabel
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.repository.PrisonerDifferencesRepository
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
@Transactional(readOnly = true)
class PrisonerDifferencesService(
  private val prisonerDifferencesRepository: PrisonerDifferencesRepository,
) {
  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  fun retrieveDifferences(label: PrisonerDifferencesLabel, from: Instant, to: Instant): List<PrisonerDifferences> =
    prisonerDifferencesRepository.findByLabelAndDateTimeBetween(label, from, to)

  @Transactional
  fun deleteOldData(): Int {
    val threshold = Instant.now().minus(28, ChronoUnit.DAYS)
    log.info("Deleting old prisoner differences before $threshold")

    val rows = prisonerDifferencesRepository.deleteByDateTimeBefore(threshold)

    log.info("Deleted $rows old prisoner differences")
    return rows
  }
}
