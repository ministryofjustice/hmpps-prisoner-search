package uk.gov.justice.digital.hmpps.prisonersearch.indexer.repository

import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.commons.lang3.builder.EqualsBuilder
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.fail
import org.opensearch.action.get.GetRequest
import org.opensearch.client.RequestOptions
import org.opensearch.client.RestHighLevelClient
import org.opensearch.client.indices.GetIndexRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.data.elasticsearch.UncategorizedElasticsearchException
import uk.gov.justice.digital.hmpps.prisonersearch.common.config.OpenSearchIndexConfiguration
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Address
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.CurrentIncentive
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IncentiveLevel
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.PrisonerAlert
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.PrisonerAlias
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.IntegrationTestBase
import java.time.LocalDate
import java.time.LocalDateTime

internal class PrisonerRepositoryTest : IntegrationTestBase() {

  @Autowired
  lateinit var highLevelClient: RestHighLevelClient

  @Nested
  inner class Count {
    @Test
    fun `will count the number of prisoners in an empty index`() {
      assertThat(prisonerRepository.count()).isEqualTo(0)
    }

    @Test
    fun `will count the prisoners`() {
      prisonerRepository.save(Prisoner().also { it.prisonerNumber = "X12341" })
      prisonerRepository.save(Prisoner().also { it.prisonerNumber = "X12342" })
      prisonerRepository.save(Prisoner().also { it.prisonerNumber = "X12343" })
      prisonerRepository.save(Prisoner().also { it.prisonerNumber = "X12344" })

      await untilCallTo { prisonerRepository.count() } matches { it == 4L }
    }

    @Test
    fun `will return -1 if index doesn't exist`() {
      assertThat(prisonerRepository.count()).isEqualTo(0L)
      prisonerRepository.deleteIndex()
      assertThat(prisonerRepository.count()).isEqualTo(-1L)
    }
  }

  @Nested
  inner class Get {
    @Test
    fun `will get prisoner`() {
      prisonerRepository.save(
        Prisoner().apply {
          prisonerNumber = "ABC123D"
          croNumber = "X12345"
          shoeSize = 12
        },
      )

      val prisoner = prisonerRepository.get("ABC123D")
      assertThat(prisoner).isNotNull

      assertThat(prisoner!!.shoeSize).isEqualTo(12)
      assertThat(prisoner.croNumber).isEqualTo("X12345")
      assertThat(prisoner.prisonerNumber).isEqualTo("ABC123D")
    }
  }

  @Nested
  inner class Save {
    @Test
    fun `will save json`() {
      prisonerRepository.save(
        Prisoner().apply {
          prisonerNumber = "ABC123D"
          croNumber = "X12345"
          shoeSize = 12
        },
      )

      val json = highLevelClient.get(GetRequest(OpenSearchIndexConfiguration.PRISONER_INDEX).id("ABC123D"), RequestOptions.DEFAULT).sourceAsString

      assertThatJson(json).node("shoeSize").isEqualTo(12)
      assertThatJson(json).node("croNumber").isEqualTo("X12345")
      val prisonerDetail = gson.fromJson(json, Prisoner::class.java)
      assertThat(prisonerDetail.prisonerNumber).isEqualTo("ABC123D")
    }
  }

  @Nested
  inner class GetSummary {
    @Test
    fun `prisoner does not exist`() {
      prisonerRepository.save(Prisoner().apply { prisonerNumber = "X12345" })

      assertThat(prisonerRepository.getSummary("nonexistent")).isNull()
    }

    @Test
    fun `prisoner exists but has no bookings`() {
      prisonerRepository.save(
        Prisoner().apply {
          prisonerNumber = "X12345"
        },
      )

      assertThat(prisonerRepository.getSummary("X12345")?.prisonerNumber).isEqualTo("X12345")
      assertThat(prisonerRepository.getSummary("X12345")!!.prisoner!!.bookingId).isNull()
    }

    @Test
    fun `prisoner exists but with no incentive data`() {
      prisonerRepository.save(
        Prisoner().apply {
          prisonerNumber = "X12345"
          bookingId = "1234"
        },
      )

      assertThat(prisonerRepository.getSummary("X12345")?.prisonerNumber).isEqualTo("X12345")
      assertThat(prisonerRepository.getSummary("X12345")?.prisoner?.bookingId).isEqualTo("1234")
    }

    @Test
    fun `will get incentive data`() {
      val testIncentive = CurrentIncentive(
        IncentiveLevel("code2", "description2"),
        LocalDateTime.parse("2024-08-14T15:16:17"),
        LocalDate.parse("2024-11-27"),
      )
      prisonerRepository.save(
        Prisoner().apply {
          prisonerNumber = "X12345"
          currentIncentive = testIncentive
        },
      )

      assertThat(prisonerRepository.getSummary("X12345")!!.prisoner?.currentIncentive).isEqualTo(testIncentive)
    }
  }

  @Nested
  inner class CreatePrisoner {

    @Test
    fun `will create prisoner with data`() {
      prisonerRepository.createPrisoner(
        Prisoner().apply {
          prisonerNumber = "X12345"
          pncNumber = "PNC-1"
        },
      )
      val data = prisonerRepository.get("X12345")!!
      assertThat(data.pncNumber).isEqualTo("PNC-1")
    }

    @Test
    fun `will not update if already exists`() {
      prisonerRepository.save(
        Prisoner().apply {
          prisonerNumber = "X12345"
        },
      )

      assertThrows<UncategorizedElasticsearchException> {
        prisonerRepository.createPrisoner(
          Prisoner().apply {
            prisonerNumber = "X12345"
          },
        )
      }.also {
        assertThat(it.message).contains("version conflict, document already exists")
      }
    }
  }

  @Nested
  inner class UpdatePrisoner {

    @Test
    fun `will update prisoner with data`() {
      val aliases1 = listOf(
        PrisonerAlias(
          firstName = "John",
          lastName = "Smith",
          dateOfBirth = LocalDate.parse("1995-01-01"),
          title = "Mr",
          middleNames = null,
          gender = null,
          ethnicity = null,
          raceCode = null,
        ),
      )
      prisonerRepository.save(
        Prisoner().apply {
          prisonerNumber = "X12345"
          pncNumber = "myPNC"
          pncNumberCanonicalLong = "should-get-nulled-out"
        },
      )

      val isUpdated = prisonerRepository.updatePrisoner(
        "X12345",
        Prisoner().apply {
          prisonerNumber = "X12345"
          pncNumber = "my-new-PNC"
          pncNumberCanonicalShort = "my-short-canonical"
          aliases = aliases1
          pncNumberCanonicalLong = null
        },
        prisonerRepository.getSummary("X12345")!!,
      )
      assertThat(isUpdated).isTrue()
      val data = prisonerRepository.get("X12345")!!
      assertThat(data.pncNumber).isEqualTo("my-new-PNC")
      assertThat(data.pncNumberCanonicalShort).isEqualTo("my-short-canonical")
      assertThat(data.pncNumberCanonicalLong).isNull()
      assertThat(data.aliases).isEqualTo(aliases1)
    }

    @Test
    fun `will not update domain data`() {
      prisonerRepository.save(
        Prisoner().apply {
          prisonerNumber = "X12345"
          currentIncentive = CurrentIncentive(IncentiveLevel("code-original", "description1"), LocalDateTime.now())
          supportingPrisonId = "OLD"
        },
      )

      val isUpdated = prisonerRepository.updatePrisoner(
        "X12345",
        Prisoner().apply {
          prisonerNumber = "X12345"
          currentIncentive = CurrentIncentive(IncentiveLevel("code-new", "description1"), LocalDateTime.now())
          supportingPrisonId = "NEW"
        },
        prisonerRepository.getSummary("X12345")!!,
      )
      assertThat(isUpdated).isTrue()
      val data = prisonerRepository.get("X12345")!!
      assertThat(data.currentIncentive?.level?.code).isEqualTo("code-original")
      assertThat(data.supportingPrisonId).isEqualTo("OLD")
    }

    @Test
    fun `wrong sequence + 1 throws exception`() {
      prisonerRepository.save(
        Prisoner().apply { prisonerNumber = "X12345" },
      )

      val summary = prisonerRepository.getSummary("X12345")!!
      assertThrows<OptimisticLockingFailureException> {
        prisonerRepository.updatePrisoner(
          "X12345",
          Prisoner(),
          summary.copy(sequenceNumber = summary.sequenceNumber + 1),
        )
      }
    }

    @Test
    fun `wrong primaryTerm + 1 throws exception`() {
      prisonerRepository.save(
        Prisoner().apply { prisonerNumber = "X12345" },
      )

      val summary = prisonerRepository.getSummary("X12345")!!
      assertThrows<OptimisticLockingFailureException> {
        prisonerRepository.updatePrisoner(
          "X12345",
          Prisoner(),
          summary.copy(primaryTerm = summary.primaryTerm + 1),
        )
      }
    }

    @Test
    fun `update prisoner with identical data is detected`() {
      val prisoner = Prisoner().apply {
        prisonerNumber = "X12345"
        pncNumberCanonicalLong = "does-not-change"
      }
      prisonerRepository.save(prisoner)

      // Update is required as well as save because an update adds a lot of null values to the document
      prisonerRepository.updatePrisoner(
        "X12345",
        prisoner,
        prisonerRepository.getSummary("X12345")!!,
      )

      assertThat(
        prisonerRepository.updatePrisoner(
          "X12345",
          prisoner,
          prisonerRepository.getSummary("X12345")!!,
        ),
      ).isFalse()

      assertThat(
        prisonerRepository.updatePrisoner(
          "X12345",
          prisoner,
          prisonerRepository.getSummary("X12345")!!,
        ),
      ).isFalse()
    }
  }

  @Nested
  inner class UpdateIncentive {

    @Test
    fun `will update prisoner with new incentive data`() {
      prisonerRepository.save(Prisoner().apply { prisonerNumber = "X12345" })

      prisonerRepository.updateIncentive(
        "X12345",
        CurrentIncentive(
          IncentiveLevel("code2", "description2"),
          LocalDateTime.parse("2024-08-14T15:16:17"),
          LocalDate.parse("2024-11-27"),
        ),
        prisonerRepository.getSummary("X12345")!!,
      )
      val data = prisonerRepository.get("X12345")?.currentIncentive!!
      assertThat(data.level.code).isEqualTo("code2")
      assertThat(data.level.description).isEqualTo("description2")
      assertThat(data.dateTime).isEqualTo(LocalDateTime.parse("2024-08-14T15:16:17"))
      assertThat(data.nextReviewDate).isEqualTo(LocalDate.parse("2024-11-27"))
    }

    @Test
    fun `will update prisoner with existing incentive data`() {
      prisonerRepository.save(
        Prisoner().apply {
          prisonerNumber = "X12345"
          currentIncentive = CurrentIncentive(
            IncentiveLevel("code1", "description1"),
            LocalDateTime.parse("2023-07-13T14:15:16"),
            LocalDate.parse("2023-08-17"),
          )
        },
      )

      prisonerRepository.updateIncentive(
        "X12345",
        CurrentIncentive(
          IncentiveLevel("code2", "description2"),
          LocalDateTime.parse("2024-08-14T15:16:17"),
          LocalDate.parse("2024-11-27"),
        ),
        prisonerRepository.getSummary("X12345")!!,
      )
      val data = prisonerRepository.get("X12345")?.currentIncentive!!
      assertThat(data.level.code).isEqualTo("code2")
      assertThat(data.level.description).isEqualTo("description2")
      assertThat(data.dateTime).isEqualTo(LocalDateTime.parse("2024-08-14T15:16:17"))
      assertThat(data.nextReviewDate).isEqualTo(LocalDate.parse("2024-11-27"))
    }

    @Test
    fun `will update prisoner with no incentive data`() {
      prisonerRepository.save(
        Prisoner().apply {
          prisonerNumber = "X12345"
          currentIncentive = CurrentIncentive(
            IncentiveLevel("code1", "description1"),
            LocalDateTime.parse("2023-07-13T14:15:16"),
            LocalDate.parse("2023-08-17"),
          )
        },
      )

      prisonerRepository.updateIncentive(
        "X12345",
        null,
        prisonerRepository.getSummary("X12345")!!,
      )
      assertThat(prisonerRepository.get("X12345")!!.currentIncentive).isNull()
    }

    @Test
    fun `wrong sequence + 1 throws exception`() {
      prisonerRepository.save(
        Prisoner().apply {
          prisonerNumber = "X12345"
          currentIncentive = CurrentIncentive(
            IncentiveLevel("code1", "description1"),
            LocalDateTime.parse("2023-07-13T14:15:16"),
            LocalDate.parse("2023-08-17"),
          )
        },
      )

      val summary = prisonerRepository.getSummary("X12345")!!
      assertThrows<OptimisticLockingFailureException> {
        prisonerRepository.updateIncentive(
          "X12345",
          CurrentIncentive(
            IncentiveLevel("code2", "description2"),
            LocalDateTime.parse("2024-08-14T15:16:17"),
            LocalDate.parse("2024-11-27"),
          ),
          summary.copy(sequenceNumber = summary.sequenceNumber + 1),
        )
      }
    }

    @Test
    fun `wrong primaryTerm + 1 throws exception`() {
      prisonerRepository.save(
        Prisoner().apply {
          prisonerNumber = "X12345"
          currentIncentive = CurrentIncentive(
            IncentiveLevel("code1", "description1"),
            LocalDateTime.parse("2023-07-13T14:15:16"),
            LocalDate.parse("2023-08-17"),
          )
        },
      )

      val summary = prisonerRepository.getSummary("X12345")!!
      assertThrows<OptimisticLockingFailureException> {
        prisonerRepository.updateIncentive(
          "X12345",
          CurrentIncentive(
            IncentiveLevel("code2", "description2"),
            LocalDateTime.parse("2024-08-14T15:16:17"),
            LocalDate.parse("2024-11-27"),
          ),
          summary.copy(primaryTerm = summary.primaryTerm + 1),
        )
      }
    }

    @Test
    fun `update prisoner with identical data is detected`() {
      val prisoner = Prisoner().apply {
        prisonerNumber = "X12345"
        pncNumberCanonicalShort = "does-not-change"
      }
      prisonerRepository.save(prisoner)

      // Update is required as well as save because an update adds a lot of null values to the document
      prisonerRepository.updatePrisoner(
        "X12345",
        prisoner,
        prisonerRepository.getSummary("X12345")!!,
      )

      assertThat(
        prisonerRepository.updatePrisoner(
          "X12345",
          prisoner,
          prisonerRepository.getSummary("X12345")!!,
        ),
      ).isFalse()

      assertThat(
        prisonerRepository.updatePrisoner(
          "X12345",
          prisoner,
          prisonerRepository.getSummary("X12345")!!,
        ),
      ).isFalse()
    }
  }

  @Nested
  inner class UpdateAlerts {

    @Test
    fun `will update prisoner with new data`() {
      prisonerRepository.save(
        Prisoner().apply {
          prisonerNumber = "X12345"
          firstName = "steve"
        },
      )

      val updated = prisonerRepository.updateAlerts(
        "X12345",
        listOf(
          PrisonerAlert(
            alertType = "A",
            alertCode = "ABC",
            active = true,
            expired = false,
          ),
        ),
        prisonerRepository.getSummary("X12345")!!,
      )
      assertThat(updated).isTrue()
      val prisoner = prisonerRepository.get("X12345")
      val data = prisoner?.alerts?.first()!!
      assertThat(data.alertType).isEqualTo("A")
      assertThat(data.alertCode).isEqualTo("ABC")
      assertThat(data.active).isTrue()
      assertThat(data.expired).isFalse()

      // Ensure other data is not affected
      assertThat(prisoner.prisonerNumber).isEqualTo("X12345")
      assertThat(prisoner.firstName).isEqualTo("steve")
    }

    @Test
    fun `will update prisoner with existing data`() {
      prisonerRepository.save(
        Prisoner().apply {
          prisonerNumber = "X12345"
          alerts = listOf(
            PrisonerAlert(
              alertType = "A",
              alertCode = "ABC",
              active = true,
              expired = false,
            ),
          )
        },
      )

      prisonerRepository.updateAlerts(
        "X12345",
        listOf(
          PrisonerAlert(
            alertType = "A2",
            alertCode = "ABC2",
            active = true,
            expired = false,
          ),
          PrisonerAlert(
            alertType = "B",
            alertCode = "BCD",
            active = false,
            expired = true,
          ),
        ),
        prisonerRepository.getSummary("X12345")!!,
      )
      val alerts = prisonerRepository.get("X12345")?.alerts!!
      with(alerts.get(0)) {
        assertThat(alertType).isEqualTo("A2")
        assertThat(alertCode).isEqualTo("ABC2")
        assertThat(active).isTrue()
        assertThat(expired).isFalse()
      }
      with(alerts.get(1)) {
        assertThat(alertType).isEqualTo("B")
        assertThat(alertCode).isEqualTo("BCD")
        assertThat(active).isFalse()
        assertThat(expired).isTrue()
      }
      assertThat(alerts).hasSize(2)
    }

    @Test
    fun `will update prisoner with no data`() {
      prisonerRepository.save(
        Prisoner().apply {
          prisonerNumber = "X12345"
          alerts = listOf(
            PrisonerAlert(
              alertType = "A",
              alertCode = "ABC",
              active = true,
              expired = false,
            ),
          )
        },
      )

      prisonerRepository.updateAlerts(
        "X12345",
        null,
        prisonerRepository.getSummary("X12345")!!,
      )
      assertThat(prisonerRepository.get("X12345")!!.alerts).isNull()
    }

    @Test
    fun `will update prisoner with empty data`() {
      prisonerRepository.save(
        Prisoner().apply {
          prisonerNumber = "X12345"
          alerts = listOf(
            PrisonerAlert(
              alertType = "A",
              alertCode = "ABC",
              active = true,
              expired = false,
            ),
          )
        },
      )

      prisonerRepository.updateAlerts(
        "X12345",
        emptyList(),
        prisonerRepository.getSummary("X12345")!!,
      )
      assertThat(prisonerRepository.get("X12345")!!.alerts).isEmpty()
    }

    @Test
    fun `will not update prisoner when data is unchanged`() {
      prisonerRepository.save(
        Prisoner().apply {
          prisonerNumber = "X12345"
          alerts = listOf(
            PrisonerAlert(
              alertType = "A",
              alertCode = "ABC",
              active = true,
              expired = false,
            ),
          )
        },
      )

      val updated = prisonerRepository.updateAlerts(
        "X12345",
        listOf(
          PrisonerAlert(
            alertType = "A",
            alertCode = "ABC",
            active = true,
            expired = false,
          ),
        ),
        prisonerRepository.getSummary("X12345")!!,
      )

      assertThat(updated).isFalse()
    }
  }

  @Nested
  inner class Delete {

    @Test
    fun `will delete prisoner`() {
      prisonerRepository.save(Prisoner().apply { prisonerNumber = "X12345" })
      assertThat(highLevelClient.get(GetRequest(OpenSearchIndexConfiguration.PRISONER_INDEX).id("X12345"), RequestOptions.DEFAULT).isExists).isTrue()

      prisonerRepository.delete(prisonerNumber = "X12345")

      assertThat(
        highLevelClient.get(
          GetRequest(OpenSearchIndexConfiguration.PRISONER_INDEX).id("X12345"),
          RequestOptions.DEFAULT,
        ).isExists,
      ).isFalse()
    }
  }

  @Nested
  inner class DeleteIndex {
    @BeforeEach
    fun setUp() {
      deletePrisonerIndices()
    }

    @Test
    fun `will delete an existing index`() {
      prisonerRepository.createIndex()

      assertThat(highLevelClient.indices().exists(GetIndexRequest(OpenSearchIndexConfiguration.PRISONER_INDEX), RequestOptions.DEFAULT)).isTrue()

      prisonerRepository.deleteIndex()

      assertThat(highLevelClient.indices().exists(GetIndexRequest(OpenSearchIndexConfiguration.PRISONER_INDEX), RequestOptions.DEFAULT)).isFalse()
    }

    @Test
    fun `will not complain if index to delete does not exist`() {
      assertThat(highLevelClient.indices().exists(GetIndexRequest(OpenSearchIndexConfiguration.PRISONER_INDEX), RequestOptions.DEFAULT)).isFalse()

      prisonerRepository.deleteIndex()

      assertThat(highLevelClient.indices().exists(GetIndexRequest(OpenSearchIndexConfiguration.PRISONER_INDEX), RequestOptions.DEFAULT)).isFalse()
    }
  }

  @Nested
  inner class DoesIndexExist {
    @BeforeEach
    fun setUp() {
      deletePrisonerIndices()
    }

    @Test
    fun `will report true when index exists`() {
      prisonerRepository.createIndex()
      assertThat(prisonerRepository.doesIndexExist()).isTrue()
    }

    @Test
    fun `will report false when index does not exists`() {
      assertThat(prisonerRepository.doesIndexExist()).isFalse()
    }
  }

  @Nested
  inner class CopyPrisoner {
    val prisoner = Prisoner().apply {
      prisonerNumber = "X12345"
      currentIncentive = CurrentIncentive(
        IncentiveLevel("code1", "description1"),
        LocalDateTime.parse("2023-07-13T14:15:16"),
        LocalDate.parse("2023-08-17"),
      )
      build = "build"
      addresses = listOf(Address("fullAddress", "postalCode", LocalDate.parse("2023-08-17"), true))
    }

    @Test
    fun `will make an accurate copy`() {
      assertThat(EqualsBuilder.reflectionEquals(prisoner, prisonerRepository.copyPrisoner(prisoner))).isTrue
    }

    @Test
    fun `copy is a different object`() {
      assertThat(prisonerRepository.copyPrisoner(prisoner)).isNotSameAs(prisoner)
      assertThat(prisonerRepository.copyPrisoner(prisoner).currentIncentive!!.level).isNotSameAs(prisoner.currentIncentive!!.level)
    }
  }
}

fun Map<*, *>.value(property: String): String {
  val properties = property.split(".")
  var currentMap = this
  properties.forEach {
    when (val currentValue = currentMap[it]) {
      is Map<*, *> -> currentMap = currentValue
      is String -> return currentValue
    }
  }
  fail("$property not found")
}
