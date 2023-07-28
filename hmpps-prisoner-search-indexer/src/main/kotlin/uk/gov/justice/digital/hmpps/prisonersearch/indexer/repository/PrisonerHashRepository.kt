package uk.gov.justice.digital.hmpps.prisonersearch.indexer.repository

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.Hibernate
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant

/*
 * This table is used to keep track of the last prisoner inserted/updated event to prevent duplicate events from being published.
 *
 * It does this by recording the hash of the Prisoner object the last time the event was sent.
 *
 * The method `upsertIfChanged` returns 1 if the hash is new indicating the prisoner event has not yet been sent. It returns 0 if the hash already exists indicating the prisoner event has already been sent.
 */
@Repository
interface PrisonerHashRepository : JpaRepository<PrisonerHash, String> {
  /*
   * The strange syntax is how Postgres handles an upsert - if there is a conflict on the insert the update is run instead.
   */
  @Modifying
  @Query(
    value = "INSERT INTO prisoner_hash (prisoner_number, prisoner_hash, updated_date_time) VALUES (:prisonerNumber, :prisonerHash, :updatedDateTime) " +
      "ON CONFLICT (prisoner_number) DO UPDATE " +
      "SET prisoner_hash=:prisonerHash, updated_date_time=:updatedDateTime WHERE prisoner_hash.prisoner_hash<>:prisonerHash",
    nativeQuery = true,
  )
  fun upsertIfChanged(prisonerNumber: String, prisonerHash: String, updatedDateTime: Instant = Instant.now()): Int
}

@Entity
@Table(name = "prisoner_hash")
data class PrisonerHash(
  @Id
  val prisonerNumber: String = "",
  @Column(name = "prisoner_hash")
  val prisonerHash: String = "",
  val updatedDateTime: Instant = Instant.now(),
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false
    other as PrisonerHash

    return prisonerNumber == other.prisonerNumber
  }

  override fun hashCode(): Int = prisonerNumber.hashCode()
}
