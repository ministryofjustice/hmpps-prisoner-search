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
 * This table is used to keep track of the last inserted/updated event to prevent duplicate events from being published.
 *
 * It does this by recording the hash of the object the last time the event was sent.
 *
 * The method `upsertIfChanged` returns 1 if the hash is new indicating the event has not yet been sent. It returns 0 if the hash already exists indicating the event has already been sent.
 */
@Repository
interface IncentiveHashRepository : JpaRepository<IncentiveHash, String> {
  /*
   * The strange syntax is how Postgres handles an upsert - if there is a conflict on the insert the update is run instead.
   */
  @Modifying
  @Query(
    value = """INSERT INTO incentive_hash (prisoner_number, incentive_hash, updated_date_time) VALUES (:prisonerNumber, :incentiveHash, :updatedDateTime) 
      ON CONFLICT (prisoner_number) DO UPDATE
      SET incentive_hash=:incentiveHash, updated_date_time=:updatedDateTime WHERE incentive_hash.incentive_hash<>:incentiveHash""",
    nativeQuery = true,
  )
  fun upsertIfChanged(prisonerNumber: String, incentiveHash: String, updatedDateTime: Instant = Instant.now()): Int
}

@Entity
@Table(name = "incentive_hash")
data class IncentiveHash(
  @Id
  val prisonerNumber: String = "",
  @Column
  val incentiveHash: String = "",
  val updatedDateTime: Instant = Instant.now(),
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false
    other as IncentiveHash

    return prisonerNumber == other.prisonerNumber
  }

  override fun hashCode(): Int = prisonerNumber.hashCode()
}
