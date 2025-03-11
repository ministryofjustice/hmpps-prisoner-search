package uk.gov.justice.digital.hmpps.prisonersearch.indexer

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.gson.Gson
import com.microsoft.applicationinsights.TelemetryClient
import org.apache.commons.lang3.RandomStringUtils
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.whenever
import org.opensearch.client.RequestOptions
import org.opensearch.client.RestHighLevelClient
import org.opensearch.client.core.CountRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.reactive.server.WebTestClient
import software.amazon.awssdk.services.sns.SnsAsyncClient
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest
import software.amazon.awssdk.services.sqs.model.DeleteMessageResponse
import software.amazon.awssdk.services.sqs.model.Message
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import uk.gov.justice.digital.hmpps.prisonersearch.common.dps.IncentiveLevel
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.CurrentIncentive
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IndexStatus
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.SyncIndex
import uk.gov.justice.digital.hmpps.prisonersearch.common.nomis.Alert
import uk.gov.justice.digital.hmpps.prisonersearch.common.nomis.Alias
import uk.gov.justice.digital.hmpps.prisonersearch.common.nomis.AssignedLivingUnit
import uk.gov.justice.digital.hmpps.prisonersearch.common.nomis.OffenceHistoryDetail
import uk.gov.justice.digital.hmpps.prisonersearch.common.nomis.OffenderBooking
import uk.gov.justice.digital.hmpps.prisonersearch.common.nomis.OffenderIdentifier
import uk.gov.justice.digital.hmpps.prisonersearch.common.nomis.PhysicalAttributes
import uk.gov.justice.digital.hmpps.prisonersearch.common.nomis.PhysicalCharacteristic
import uk.gov.justice.digital.hmpps.prisonersearch.common.nomis.PhysicalMark
import uk.gov.justice.digital.hmpps.prisonersearch.common.nomis.ProfileInformation
import uk.gov.justice.digital.hmpps.prisonersearch.common.nomis.SentenceDetail
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.GsonConfig
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.repository.IndexStatusRepository
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.repository.PrisonerRepository
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.IndexQueueService
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.MaintainIndexService
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.EventMessage
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.MsgBody
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.wiremock.HmppsAuthApiExtension
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.wiremock.IncentivesApiExtension
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.wiremock.PrisonApiExtension
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.wiremock.RestrictedPatientsApiExtension
import uk.gov.justice.hmpps.sqs.HmppsQueueFactory
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.HmppsSqsProperties
import uk.gov.justice.hmpps.sqs.HmppsTopicFactory
import uk.gov.justice.hmpps.sqs.MissingQueueException
import uk.gov.justice.hmpps.sqs.MissingTopicException
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue
import uk.gov.justice.hmpps.test.kotlin.auth.JwtAuthorisationHelper
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.random.Random

@ExtendWith(
  IncentivesApiExtension::class,
  PrisonApiExtension::class,
  RestrictedPatientsApiExtension::class,
  HmppsAuthApiExtension::class,
)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(IntegrationTestBase.SqsConfig::class)
@ActiveProfiles("test")
abstract class IntegrationTestBase {
  @Autowired
  protected lateinit var jwtAuthHelper: JwtAuthorisationHelper

  @Autowired
  lateinit var openSearchClient: RestHighLevelClient

  @Autowired
  lateinit var webTestClient: WebTestClient

  @MockitoSpyBean
  protected lateinit var indexQueueService: IndexQueueService

  @MockitoSpyBean
  lateinit var hmppsQueueService: HmppsQueueService

  @Autowired
  lateinit var prisonerRepository: PrisonerRepository

  @Autowired
  lateinit var indexStatusRepository: IndexStatusRepository

  @MockitoSpyBean
  protected lateinit var maintainIndexService: MaintainIndexService

  @MockitoSpyBean
  lateinit var prisonerSpyBeanRepository: PrisonerRepository

  @MockitoSpyBean
  lateinit var telemetryClient: TelemetryClient

  @Autowired
  internal lateinit var gson: Gson

  @MockitoSpyBean
  lateinit var clock: Clock

  @Autowired
  protected lateinit var objectMapper: ObjectMapper

  protected val indexQueue by lazy { hmppsQueueService.findByQueueId("index") ?: throw MissingQueueException("HmppsQueue indexqueue not found") }
  protected val hmppsDomainQueue by lazy { hmppsQueueService.findByQueueId("hmppsdomainqueue") ?: throw MissingQueueException("HmppsQueue hmppsdomainqueue not found") }
  protected val offenderQueue by lazy { hmppsQueueService.findByQueueId("offenderqueue") ?: throw MissingQueueException("HmppsQueue offenderqueue not found") }
  protected val hmppsEventTopic by lazy { hmppsQueueService.findByTopicId("hmppseventtopic") ?: throw MissingQueueException("HmppsTopic hmpps event topic not found") }
  protected val hmppsEventsQueue by lazy { hmppsQueueService.findByQueueId("hmppseventtestqueue") ?: throw MissingQueueException("hmppseventtestqueue not found") }

  internal val indexSqsClient by lazy { indexQueue.sqsClient }
  internal val indexQueueUrl by lazy { indexQueue.queueUrl }
  internal val indexSqsDlqClient by lazy { indexQueue.sqsDlqClient }
  internal val indexDlqUrl by lazy { indexQueue.dlqUrl as String }
  internal val indexQueueName by lazy { indexQueue.queueName }
  internal val indexDlqName by lazy { indexQueue.dlqName as String }

  internal val hmppsDomainSqsClient by lazy { hmppsDomainQueue.sqsClient }
  internal val hmppsDomainQueueUrl by lazy { hmppsDomainQueue.queueUrl }

  internal val offenderSqsClient by lazy { offenderQueue.sqsClient }
  internal val offenderQueueUrl by lazy { offenderQueue.queueUrl }
  internal val offenderQueueName by lazy { offenderQueue.queueName }
  internal val offenderDlqName by lazy { offenderQueue.dlqName as String }

  internal val hmppsEventsQueueClient by lazy { hmppsEventsQueue.sqsClient }
  internal val hmppsEventsQueueUrl by lazy { hmppsEventsQueue.queueUrl }

  @BeforeEach
  fun cleanOpenSearch() {
    deletePrisonerIndices()
    indexSqsClient.purgeQueue(PurgeQueueRequest.builder().queueUrl(indexQueueUrl).build()).get()
    indexSqsDlqClient?.purgeQueue(PurgeQueueRequest.builder().queueUrl(indexDlqUrl).build())?.get()
    createPrisonerIndices()
    initialiseIndexStatus()
  }

  @BeforeEach
  fun mockClock() {
    val fixedClock = Clock.fixed(Instant.parse("2022-09-16T10:40:34Z"), ZoneId.of("UTC"))
    whenever(clock.instant()).thenReturn(fixedClock.instant())
    whenever(clock.zone).thenReturn(fixedClock.zone)
  }

  protected fun purgeDomainEventsQueue() {
    hmppsEventsQueueClient.purgeQueue(PurgeQueueRequest.builder().queueUrl(hmppsEventsQueueUrl).build()).get()
  }

  val hmppsEventTopicName by lazy { hmppsEventTopic.arn }

  fun createPrisonerIndices() = SyncIndex.entries.forEach { prisonerRepository.createIndex(it) }

  fun deletePrisonerIndices() = SyncIndex.entries.forEach { prisonerRepository.deleteIndex(it) }

  fun initialiseIndexStatus() {
    indexStatusRepository.deleteAll()
    indexStatusRepository.save(IndexStatus.newIndex())
  }

  fun deinitialiseIndexStatus() = indexStatusRepository.deleteAll()

  fun buildAndSwitchIndex(expectedCount: Long) {
    buildIndex(expectedCount)

    webTestClient.put()
      .uri("/maintain-index/mark-complete")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
      .exchange()
      .expectStatus().isOk

    await untilCallTo { getIndexCount(SyncIndex.RED) } matches { it == expectedCount }
  }

  fun buildIndex(expectedCount: Long) {
    webTestClient.put()
      .uri("/maintain-index/build")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
      .exchange()
      .expectStatus().isOk

    await untilCallTo { indexQueueService.getIndexQueueStatus().active } matches { it == false }
    await untilCallTo { getIndexCount(SyncIndex.RED) } matches { it == expectedCount }
  }

  fun getIndexCount(index: SyncIndex) = getIndexCount(index.indexName)
  fun getIndexCount(index: String): Long = openSearchClient.count(CountRequest(index), RequestOptions.DEFAULT).count

  protected fun getNumberOfMessagesCurrentlyOnDomainQueue(): Int = hmppsEventsQueue.sqsClient.countAllMessagesOnQueue(hmppsEventsQueue.queueUrl).get()

  protected fun SqsAsyncClient.receiveFirstMessage(): Message = receiveMessage(
    ReceiveMessageRequest.builder().queueUrl(hmppsEventsQueue.queueUrl).build(),
  ).get().messages().first()

  protected fun SqsAsyncClient.deleteLastMessage(result: Message): DeleteMessageResponse = deleteMessage(
    DeleteMessageRequest.builder().queueUrl(hmppsEventsQueue.queueUrl).receiptHandle(result.receiptHandle()).build(),
  ).get()

  protected fun readNextDomainEventMessage(): String {
    val updateResult = hmppsEventsQueue.sqsClient.receiveFirstMessage()
    hmppsEventsQueue.sqsClient.deleteLastMessage(updateResult)
    return objectMapper.readValue<MsgBody>(updateResult.body()).Message
  }

  protected fun readEventFromNextDomainEventMessage(): String {
    val message = readNextDomainEventMessage()
    return objectMapper.readValue<EventMessage>(message).eventType
  }

  internal fun setAuthorisation(
    user: String = "prisoner-offender-search-indexer-client",
    roles: List<String> = listOf(),
  ): (HttpHeaders) -> Unit = jwtAuthHelper.setAuthorisationHeader(username = user, roles = roles)

  /* Need to redefine these beans so that we can then spy on them in tests */
  @Suppress("SpringJavaInjectionPointsAutowiringInspection")
  @TestConfiguration
  class SqsConfig(private val hmppsQueueFactory: HmppsQueueFactory, private val hmppsTopicFactory: HmppsTopicFactory) {

    @Bean("offenderqueue-sqs-client")
    fun offenderQueueSqsClient(
      hmppsSqsProperties: HmppsSqsProperties,
      @Qualifier("offenderqueue-sqs-dlq-client") offenderQueueSqsDlqClient: SqsAsyncClient,
    ): SqsAsyncClient = with(hmppsSqsProperties) {
      val config = queues["offenderqueue"]
        ?: throw MissingQueueException("HmppsSqsProperties config for offenderqueue not found")
      hmppsQueueFactory.createSqsAsyncClient(config, hmppsSqsProperties, offenderQueueSqsDlqClient)
    }

    @Bean("publish-sqs-client")
    fun publishSqsClient(
      hmppsSqsProperties: HmppsSqsProperties,
      @Qualifier("publish-sqs-dlq-client") publishSqsDlqClient: SqsAsyncClient,
    ): SqsAsyncClient = with(hmppsSqsProperties) {
      val config = queues["publish"]
        ?: throw MissingQueueException("HmppsSqsProperties config for publish not found")
      hmppsQueueFactory.createSqsAsyncClient(config, hmppsSqsProperties, publishSqsDlqClient)
    }

    @Bean("hmppsdomainqueue-sqs-client")
    fun hmppsDomainQueueSqsClient(
      hmppsSqsProperties: HmppsSqsProperties,
      @Qualifier("hmppsdomainqueue-sqs-dlq-client") hmppsDomainQueueSqsDlqClient: SqsAsyncClient,
    ): SqsAsyncClient = with(hmppsSqsProperties) {
      val config = queues["hmppsdomainqueue"]
        ?: throw MissingQueueException("HmppsSqsProperties config for hmppsdomainqueue not found")
      hmppsQueueFactory.createSqsAsyncClient(config, hmppsSqsProperties, hmppsDomainQueueSqsDlqClient)
    }

    @Bean("hmppseventtopic-sns-client")
    fun eventTopicSnsClient(
      hmppsSqsProperties: HmppsSqsProperties,
    ): SnsAsyncClient = with(hmppsSqsProperties) {
      val config = topics["hmppseventtopic"]
        ?: throw MissingTopicException("HmppsSqsProperties config for hmppseventtopic not found")
      hmppsTopicFactory.createSnsAsyncClient("hmppseventtopic", config, hmppsSqsProperties)
    }
  }
}

data class PrisonerBuilder(
  val prisonerNumber: String = generatePrisonerNumber(),
  val bookingId: Long? = generateBookingId(),
  val title: String = "Mr",
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
  val assignedLivingUnitLocationId: Long? = Random.nextLong(),
  val sentenceDetail: SentenceDetail? = null,
  val convictedStatus: String? = null,
  val allConvictedOffences: List<OffenceHistoryDetail>? = null,
) {

  fun toOffenderBooking(): String {
    val offenderId = Random.nextLong()
    return GsonConfig().gson().toJson(
      OffenderBooking(
        offenderNo = this.prisonerNumber,
        bookingNo = "V61587",
        bookingId = this.bookingId,
        offenderId = offenderId,
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
          locationId = this.assignedLivingUnitLocationId,
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
            // In search all alerts are not expired and active
            expired = false,
            active = true,
            dateCreated = LocalDate.now(),
          )
        },
        aliases = this.aliases.map { a ->
          Alias(
            gender = a.gender,
            ethnicity = a.ethnicity,
            title = this.title,
            firstName = this.firstName,
            middleName = null,
            lastName = this.lastName,
            age = null,
            dob = LocalDate.parse(this.dateOfBirth),
            nameType = null,
            createDate = LocalDate.now(),
            offenderId = Random.nextLong(),
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
        profileInformation = listOf(
          ProfileInformation("YOUTH", "Youth Offender?", "NO"),
          ProfileInformation("RELF", "Religion", "Christian"),
          ProfileInformation("NAT", "Nationality?", "British"),
        ),
        inOutStatus = "IN",
        allIdentifiers = listOf(
          OffenderIdentifier(
            offenderId = offenderId,
            type = "CRO",
            value = "29906/12L",
            issuedAuthorityText = null,
            issuedDate = LocalDate.parse("2013-12-02"),
            whenCreated = LocalDateTime.parse("2013-12-02T20:00"),
          ),
          OffenderIdentifier(
            offenderId = offenderId,
            type = "PNC",
            value = "12/394773W",
            issuedAuthorityText = null,
            issuedDate = LocalDate.parse("2013-12-02"),
            whenCreated = LocalDateTime.parse("2013-12-02T20:00"),
          ),
        ),
        status = "ACTIVE IN",
        legalStatus = "REMAND",
        imprisonmentStatus = "LIFE",
        imprisonmentStatusDescription = "Life imprisonment",
        convictedStatus = convictedStatus,
        latestLocationId = "WWI",
        sentenceDetail = this.sentenceDetail,
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
  }

  fun toIncentiveLevel(): String = Gson().toJson(
    this.currentIncentive?.let {
      IncentiveLevel(
        iepCode = it.level.code,
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

fun letters(length: Int): String = RandomStringUtils.insecure().next(length, true, true)

fun numbers(length: Int): String = RandomStringUtils.insecure().next(length, false, true)

fun String.readResourceAsText(): String = IntegrationTestBase::class.java.getResource(this)!!.readText()
