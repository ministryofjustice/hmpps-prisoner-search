package uk.gov.justice.digital.hmpps.prisonersearch.indexer.repository

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.Hibernate
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
interface PrisonerDifferencesRepository : JpaRepository<PrisonerDifferences, UUID> {
  fun findByNomsNumber(nomsNumber: String): List<PrisonerDifferences>
  fun findByLabelAndDateTimeBetween(label: PrisonerDifferencesLabel, from: Instant, to: Instant): List<PrisonerDifferences>
  fun deleteByDateTimeBefore(to: Instant): Int
}

@Entity
@Table(name = "prisoner_differences")
class PrisonerDifferences(
  @Id
  @GeneratedValue
  val prisonerDifferencesId: UUID? = null,

  val nomsNumber: String,

  val differences: String,

  @Enumerated(EnumType.STRING)
  val label: PrisonerDifferencesLabel,

  val dateTime: Instant = Instant.now(),
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false
    return prisonerDifferencesId == (other as PrisonerDifferences).prisonerDifferencesId
  }

  override fun hashCode(): Int = prisonerDifferencesId?.hashCode() ?: 0

  override fun toString(): String =
    "PrisonerDifferences(prisonerDifferencesId=$prisonerDifferencesId, nomsNumber='$nomsNumber', differences='$differences', dateTime=$dateTime)"
}

enum class PrisonerDifferencesLabel { GREEN_BLUE, RED }
