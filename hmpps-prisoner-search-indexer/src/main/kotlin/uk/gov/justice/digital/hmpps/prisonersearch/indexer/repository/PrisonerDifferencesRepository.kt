package uk.gov.justice.digital.hmpps.prisonersearch.indexer.repository

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.Hibernate
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
interface PrisonerDifferencesRepository : JpaRepository<PrisonerDifferences, UUID> {
  fun findByNomsNumber(nomsNumber: String): List<PrisonerDifferences>
  fun findByLabelAndDateTimeBetween(label: PrisonerDifferencesLabel, from: Instant, to: Instant): List<PrisonerDifferences>

  // This query needs to be native to avoid JPA doing a lot of unnecessary extra work
  // and running out of memory when there is a lot of rows to delete
  // (e.g. JPA would raise events for each row deleted)
  @Query("delete from PRISONER_DIFFERENCES p where p.DATE_TIME < :to", nativeQuery = true)
  @Modifying
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
