package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.CurrentIncentive
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.TelemetryEvents
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.repository.IncentiveHashRepository
import java.time.Instant

@Service
class IncentiveDifferenceService(
  private val incentiveHashRepository: IncentiveHashRepository,
  private val prisonerDifferenceService: PrisonerDifferenceService,
) {
  @Transactional
  fun handleDifferences(
    prisonerNumber: String,
    bookingId: Long,
    previousIncentiveSnapshot: CurrentIncentive?,
    incentive: CurrentIncentive,
    eventType: String,
  ) {
    prisonerDifferenceService.hash(incentive)!!.run {
      takeIf { updateDbHash(prisonerNumber, it) }?.run {
        prisonerDifferenceService.generateDiffEvent(previousIncentiveSnapshot, prisonerNumber, incentive)
        prisonerDifferenceService.generateDiffTelemetry(previousIncentiveSnapshot, prisonerNumber, bookingId, incentive, eventType)
      } ?: prisonerDifferenceService.raiseTelemetry(TelemetryEvents.INCENTIVE_DATABASE_NO_CHANGE, prisonerNumber, bookingId, eventType)
    }
  }

  private fun updateDbHash(nomsNumber: String, hash: String) =
    // upsertIfChanged returns the number of records altered, so > 0 means that we have changed something
    incentiveHashRepository.upsertIfChanged(nomsNumber, hash, Instant.now()) > 0
}
