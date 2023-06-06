package uk.gov.justice.digital.hmpps.prisonersearchindexer.integration

import com.google.gson.Gson
import com.microsoft.applicationinsights.TelemetryClient
import org.apache.commons.lang3.RandomStringUtils
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.opensearch.client.RequestOptions
import org.opensearch.client.RestHighLevelClient
import org.opensearch.client.core.CountRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest
import uk.gov.justice.digital.hmpps.prisonersearchindexer.config.GsonConfig
import uk.gov.justice.digital.hmpps.prisonersearchindexer.helpers.JwtAuthHelper
import uk.gov.justice.digital.hmpps.prisonersearchindexer.integration.wiremock.HmppsAuthApiExtension
import uk.gov.justice.digital.hmpps.prisonersearchindexer.integration.wiremock.IncentivesApiExtension
import uk.gov.justice.digital.hmpps.prisonersearchindexer.integration.wiremock.PrisonApiExtension
import uk.gov.justice.digital.hmpps.prisonersearchindexer.integration.wiremock.RestrictedPatientsApiExtension
import uk.gov.justice.digital.hmpps.prisonersearchindexer.model.CurrentIncentive
import uk.gov.justice.digital.hmpps.prisonersearchindexer.model.IndexStatus
import uk.gov.justice.digital.hmpps.prisonersearchindexer.model.SyncIndex
import uk.gov.justice.digital.hmpps.prisonersearchindexer.repository.IndexStatusRepository
import uk.gov.justice.digital.hmpps.prisonersearchindexer.repository.PrisonerRepository
import uk.gov.justice.digital.hmpps.prisonersearchindexer.services.IncentiveLevel
import uk.gov.justice.digital.hmpps.prisonersearchindexer.services.IndexQueueService
import uk.gov.justice.digital.hmpps.prisonersearchindexer.services.dto.nomis.Alert
import uk.gov.justice.digital.hmpps.prisonersearchindexer.services.dto.nomis.Alias
import uk.gov.justice.digital.hmpps.prisonersearchindexer.services.dto.nomis.AssignedLivingUnit
import uk.gov.justice.digital.hmpps.prisonersearchindexer.services.dto.nomis.OffenderBooking
import uk.gov.justice.digital.hmpps.prisonersearchindexer.services.dto.nomis.PhysicalAttributes
import uk.gov.justice.digital.hmpps.prisonersearchindexer.services.dto.nomis.PhysicalCharacteristic
import uk.gov.justice.digital.hmpps.prisonersearchindexer.services.dto.nomis.PhysicalMark
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.MissingQueueException
import java.time.LocalDate
import kotlin.random.Random

@ExtendWith(
  IncentivesApiExtension::class,
  PrisonApiExtension::class,
  RestrictedPatientsApiExtension::class,
  HmppsAuthApiExtension::class,
)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
abstract class IntegrationTestBase {
  @Autowired
  protected lateinit var jwtAuthHelper: JwtAuthHelper

  @SpyBean
  lateinit var telemetryClient: TelemetryClient

  @Autowired
  lateinit var elasticSearchClient: RestHighLevelClient

  @Autowired
  lateinit var webTestClient: WebTestClient

  @SpyBean
  protected lateinit var indexQueueService: IndexQueueService

  @SpyBean
  lateinit var hmppsQueueService: HmppsQueueService

  @Autowired
  lateinit var prisonerRespository: PrisonerRepository

  @Autowired
  lateinit var indexStatusRepository: IndexStatusRepository

  @Autowired
  internal lateinit var gson: Gson

  internal val indexAwsSqsClient by lazy { indexQueue.sqsClient }
  internal val indexQueueUrl by lazy { indexQueue.queueUrl }

  internal val indexAwsSqsDlqClient by lazy { indexQueue.sqsDlqClient }
  internal val indexDlqUrl by lazy { indexQueue.dlqUrl as String }

  @BeforeEach
  fun cleanElasticsearch() {
    deletePrisonerIndices()
    indexAwsSqsClient.purgeQueue(PurgeQueueRequest.builder().queueUrl(indexQueueUrl).build()).get()
    indexAwsSqsDlqClient?.purgeQueue(PurgeQueueRequest.builder().queueUrl(indexDlqUrl).build())?.get()
    createPrisonerIndices()
  }

  protected val indexQueue by lazy { hmppsQueueService.findByQueueId("index") ?: throw MissingQueueException("HmppsQueue indexqueue not found") }
  protected val hmppsEventTopic by lazy { hmppsQueueService.findByTopicId("hmppseventtopic") ?: throw MissingQueueException("HmppsTopic hmpps event topic not found") }

  val indexQueueName by lazy { indexQueue.queueName }
  val indexDlqName by lazy { indexQueue.dlqName as String }
  val hmppsEventTopicName by lazy { hmppsEventTopic.arn }

  fun createPrisonerIndices() = SyncIndex.values().forEach { prisonerRespository.createIndex(it) }

  fun deletePrisonerIndices() = SyncIndex.values().forEach { prisonerRespository.deleteIndex(it) }

  fun initialiseIndexStatus() {
    indexStatusRepository.deleteAll()
    indexStatusRepository.save(IndexStatus.newIndex())
  }

  fun deinitialiseIndexStatus() = indexStatusRepository.deleteAll()

  fun buildAndSwitchIndex(index: SyncIndex, expectedCount: Long) {
    buildIndex(index, expectedCount)

    webTestClient.put()
      .uri("/prisoner-index/mark-complete")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
      .exchange()
      .expectStatus().isOk

    await untilCallTo { getIndexCount("prisoner") } matches { it == expectedCount }
  }

  fun buildIndex(index: SyncIndex, expectedCount: Long) {
    webTestClient.put()
      .uri("/prisoner-index/build-index")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
      .exchange()
      .expectStatus().isOk

    await untilCallTo { indexQueueService.getIndexQueueStatus().active } matches { it == false }
    await untilCallTo { getIndexCount(index) } matches { it == expectedCount }
  }

  fun getIndexCount(index: SyncIndex) = getIndexCount(index.indexName)
  fun getIndexCount(index: String): Long = elasticSearchClient.count(CountRequest(index), RequestOptions.DEFAULT).count

  internal fun setAuthorisation(
    user: String = "prisoner-offender-search-indexer-client",
    roles: List<String> = listOf(),
  ): (HttpHeaders) -> Unit = jwtAuthHelper.setAuthorisation(user, roles)
}

data class PrisonerBuilder(
  val prisonerNumber: String = generatePrisonerNumber(),
  val bookingId: Long? = generateBookingId(),
  val firstName: String = "LUCAS",
  val lastName: String = "MORALES",
  val agencyId: String = "MDI",
  val released: Boolean = false,
  val alertCodes: List<Pair<String, String>> = listOf(),
  val dateOfBirth: String = "1965-07-19",
  val cellLocation: String = "A-1-1",
  val heightCentimetres: Int? = null,
  val weightKilograms: Int? = null,
  val gender: String? = null,
  val ethnicity: String? = null,
  val aliases: List<AliasBuilder> = listOf(),
  val physicalCharacteristics: PhysicalCharacteristicBuilder? = null,
  val physicalMarks: PhysicalMarkBuilder? = null,
  val currentIncentive: CurrentIncentive? = null,
) {

  private fun getOffenderBookingTemplate(): OffenderBooking =
    GsonConfig().gson().fromJson("/templates/booking.json".readResourceAsText(), OffenderBooking::class.java)

  fun toOffenderBooking(): String = GsonConfig().gson().toJson(
    getOffenderBookingTemplate().copy(
      offenderNo = this.prisonerNumber,
      bookingId = this.bookingId,
      firstName = this.firstName,
      lastName = this.lastName,
      agencyId = this.agencyId,
      dateOfBirth = LocalDate.parse(this.dateOfBirth),
      physicalAttributes = PhysicalAttributes(
        gender = this.gender,
        raceCode = null,
        ethnicity = this.ethnicity,
        heightFeet = null,
        heightInches = null,
        heightMetres = null,
        heightCentimetres = this.heightCentimetres,
        weightPounds = null,
        weightKilograms = this.weightKilograms,
      ),
      assignedLivingUnit = AssignedLivingUnit(
        agencyId = this.agencyId,
        locationId = Random.nextLong(),
        description = this.cellLocation,
        agencyName = "$agencyId (HMP)",
      ),
      alerts = this.alertCodes.map { (type, code) ->
        Alert(
          alertId = Random.nextLong(),
          offenderNo = this.prisonerNumber,
          alertCode = code,
          alertCodeDescription = "Code description for $code",
          alertType = type,
          alertTypeDescription = "Type Description for $type",
          expired = false, // In search all alerts are not expired and active
          active = true,
          dateCreated = LocalDate.now(),
        )
      },
      aliases = this.aliases.map { a ->
        Alias(
          gender = a.gender,
          ethnicity = a.ethnicity,
          firstName = this.firstName,
          middleName = null,
          lastName = this.lastName,
          age = null,
          dob = LocalDate.parse(this.dateOfBirth),
          nameType = null,
          createDate = LocalDate.now(),
        )
      },
      physicalCharacteristics = mutableListOf<PhysicalCharacteristic>().also { pcs ->
        this.physicalCharacteristics?.hairColour?.let {
          pcs.add(PhysicalCharacteristic("HAIR", "Hair Colour", it, null))
        }
        this.physicalCharacteristics?.rightEyeColour?.let {
          pcs.add(PhysicalCharacteristic("R_EYE_C", "Right Eye Colour", it, null))
        }
        this.physicalCharacteristics?.leftEyeColour?.let {
          pcs.add(PhysicalCharacteristic("L_EYE_C", "Left Eye Colour", it, null))
        }
        this.physicalCharacteristics?.facialHair?.let {
          pcs.add(PhysicalCharacteristic("FACIAL_HAIR", "Facial Hair", it, null))
        }
        this.physicalCharacteristics?.shapeOfFace?.let {
          pcs.add(PhysicalCharacteristic("FACE", "Shape of Face", it, null))
        }
        this.physicalCharacteristics?.build?.let {
          pcs.add(PhysicalCharacteristic("BUILD", "Build", it, null))
        }
        this.physicalCharacteristics?.shoeSize?.let {
          pcs.add(PhysicalCharacteristic("SHOESIZE", "Shoe Size", it.toString(), null))
        }
      },
      physicalMarks = mutableListOf<PhysicalMark>().also { pms ->
        this.physicalMarks?.tattoo?.forEach {
          pms.add(PhysicalMark("Tattoo", null, it.bodyPart, null, it.comment, null))
        }
        this.physicalMarks?.mark?.forEach {
          pms.add(PhysicalMark("Mark", null, it.bodyPart, null, it.comment, null))
        }
        this.physicalMarks?.other?.forEach {
          pms.add(PhysicalMark("Other", null, it.bodyPart, null, it.comment, null))
        }
        this.physicalMarks?.scar?.forEach {
          pms.add(PhysicalMark("Scar", null, it.bodyPart, null, it.comment, null))
        }
      },
    ).let {
      if (released) {
        it.copy(
          status = "INACTIVE OUT",
          lastMovementTypeCode = "REL",
          lastMovementReasonCode = "HP",
          inOutStatus = "OUT",
          agencyId = "OUT",
        )
      } else {
        it.copy(
          lastMovementTypeCode = "ADM",
          lastMovementReasonCode = "I",
        )
      }
    },
  )

  fun toIncentiveLevel(): String = Gson().toJson(
    this.currentIncentive?.let {
      IncentiveLevel(
        iepCode = it.level.code ?: "",
        iepLevel = it.level.description,
        iepTime = it.dateTime,
        nextReviewDate = it.nextReviewDate,
      )
    },
  )
}

data class PhysicalCharacteristicBuilder(
  val hairColour: String? = null,
  val rightEyeColour: String? = null,
  val leftEyeColour: String? = null,
  val facialHair: String? = null,
  val shapeOfFace: String? = null,
  val build: String? = null,
  val shoeSize: Int? = null,
)
data class PhysicalMarkBuilder(
  val mark: List<BodyPartBuilder>? = null,
  val other: List<BodyPartBuilder>? = null,
  val scar: List<BodyPartBuilder>? = null,
  val tattoo: List<BodyPartBuilder>? = null,
)

data class BodyPartBuilder(
  val bodyPart: String,
  val comment: String? = null,
)

data class AliasBuilder(
  val gender: String? = null,
  val ethnicity: String? = null,
)

// generate random string starting with a letter, followed by 4 numbers and 2 letters
fun generatePrisonerNumber(): String = "${letters(1)}${numbers(4)}${letters(2)}"

// generate random number 8 digits
fun generateBookingId(): Long = numbers(8).toLong()

fun letters(length: Int): String = RandomStringUtils.random(length, true, true)

fun numbers(length: Int): String = RandomStringUtils.random(length, false, true)

fun String.readResourceAsText(): String = IntegrationTestBase::class.java.getResource(this)!!.readText()
