package uk.gov.justice.digital.hmpps.prisonersearch.indexer.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.model.nomis.Alias
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.model.nomis.OffenderBooking
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.model.nomis.OffenderIdentifier
import java.time.LocalDate

class OffenderBookingTest {
  @Test
  fun `should return latest identifier`() {
    val ob = aBooking().copy(
      allIdentifiers = listOf(
        OffenderIdentifier(
          offenderId = 1L,
          type = "PNC",
          value = "oldest_PNC",
          issuedAuthorityText = "NCA",
          issuedDate = LocalDate.now().minusYears(2),
          whenCreated = LocalDate.now().minusYears(2).atStartOfDay(),
        ),
        OffenderIdentifier(
          offenderId = 1L,
          type = "PNC",
          value = "newest_PNC",
          issuedAuthorityText = "NCA",
          issuedDate = LocalDate.now().minusYears(1),
          whenCreated = LocalDate.now().minusYears(1).atStartOfDay(),
        ),
      ),
    )

    assertThat(ob.latestIdentifier("PNC")?.value).isEqualTo("newest_PNC")
  }

  @Test
  fun `should return latest identifier from an alias`() {
    val ob = aBooking().copy(
      aliases = listOf(
        Alias(
          firstName = "Fred",
          lastName = "Blogs",
          dob = LocalDate.now().minusYears(18),
          createDate = LocalDate.now(),
          offenderId = 2L,
        ),

      ),
      allIdentifiers = listOf(
        OffenderIdentifier(
          offenderId = 1L,
          type = "PNC",
          value = "main_offender_PNC",
          issuedAuthorityText = "NCA",
          issuedDate = LocalDate.now().minusYears(2),
          whenCreated = LocalDate.now().minusYears(2).atStartOfDay(),
        ),
        OffenderIdentifier(
          offenderId = 2L,
          type = "PNC",
          value = "alias_PNC",
          issuedAuthorityText = "NCA",
          issuedDate = LocalDate.now().minusYears(1),
          whenCreated = LocalDate.now().minusYears(1).atStartOfDay(),
        ),
      ),
    )

    assertThat(ob.latestIdentifier("PNC")?.value).isEqualTo("alias_PNC")
  }

  @Test
  fun `should return identifiers from the latest booking only`() {
    val ob = aBooking().copy(
      aliases = listOf(
        Alias(
          firstName = "Fred",
          lastName = "Blogs",
          dob = LocalDate.now().minusYears(18),
          createDate = LocalDate.now(),
          offenderId = 2L,
        ),

      ),
      allIdentifiers = listOf(
        OffenderIdentifier(
          offenderId = 1L,
          type = "PNC",
          value = "main_offender_PNC",
          issuedAuthorityText = "NCA",
          issuedDate = LocalDate.now().minusYears(2),
          whenCreated = LocalDate.now().minusYears(2).atStartOfDay(),
        ),
        OffenderIdentifier(
          offenderId = 2L,
          type = "PNC",
          value = "alias_PNC",
          issuedAuthorityText = "NCA",
          issuedDate = LocalDate.now().minusYears(1),
          whenCreated = LocalDate.now().minusYears(1).atStartOfDay(),
        ),
      ),
    )

    assertThat(ob.identifiersForActiveOffender("PNC")).extracting("value").containsExactly("main_offender_PNC")
  }
}

private fun aBooking() = OffenderBooking("A1234AA", 1L, "Fred", "Bloggs", LocalDate.now().minusYears(18))
