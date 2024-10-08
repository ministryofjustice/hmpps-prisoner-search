package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.DiffCategory
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.DiffProperties
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.TelemetryEvents.EVENTS_SEND_FAILURE
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners.DomainEvent
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.PrisonerDifferences
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.HmppsDomainEventEmitter.Companion.CREATED_EVENT_TYPE
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.HmppsDomainEventEmitter.Companion.PRISONER_ALERTS_UPDATED_EVENT_TYPE
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.HmppsDomainEventEmitter.Companion.PRISONER_RECEIVED_EVENT_TYPE
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.HmppsDomainEventEmitter.Companion.PRISONER_RELEASED_EVENT_TYPE
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.HmppsDomainEventEmitter.Companion.UPDATED_EVENT_TYPE
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.HmppsDomainEventEmitter.PrisonerReceiveReason
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.HmppsDomainEventEmitter.PrisonerReleaseReason
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.PersonReference.Companion.withNomsNumber
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.eventTypeMessageAttributes
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties

@Service
class HmppsDomainEventEmitter(
  private val objectMapper: ObjectMapper,
  private val hmppsQueueService: HmppsQueueService,
  private val diffProperties: DiffProperties,
  private val clock: Clock?,
  private val telemetryClient: TelemetryClient,
  @Value("\${publish.delayInSeconds}") private val publishDelayInSeconds: Int,
) {
  private val publishQueue by lazy { hmppsQueueService.findByQueueId("publish") as HmppsQueue }
  private val publishSqsClient by lazy { publishQueue.sqsClient }
  private val publishQueueUrl by lazy { publishQueue.queueUrl }

  fun <T : PrisonerAdditionalInformation> defaultFailureHandler(event: PrisonerDomainEvent<T>, exception: Throwable) {
    log.error(
      "Failed to send event ${event.eventType} for offenderNo= ${event.additionalInformation.nomsNumber}. Event must be manually created",
      exception,
    )
    telemetryClient.trackEvent(EVENTS_SEND_FAILURE, event.asMap())
  }

  fun <T : PrisonerAdditionalInformation> PrisonerDomainEvent<T>.publish(
    onFailure: (error: Throwable) -> Unit = {
      defaultFailureHandler(this, it)
    },
  ) {
    val event = PrisonerDomainEvent(
      additionalInformation = this.additionalInformation,
      eventType = "${diffProperties.prefix}${this.eventType}",
      occurredAt = this.occurredAt,
      version = this.version,
      description = this.description,
      detailUrl = this.detailUrl,
      personReference = this.personReference,
    )

    val domainEvent = DomainEvent(eventType = event.eventType, body = objectMapper.writeValueAsString(event))

    val request = SendMessageRequest.builder().queueUrl(publishQueueUrl)
      .messageBody(objectMapper.writeValueAsString(domainEvent))
      .eventTypeMessageAttributes(event.eventType)
      .delaySeconds(publishDelayInSeconds)
      .build()

    runCatching {
      publishSqsClient.sendMessage(request).get()
      telemetryClient.trackEvent(event.eventType, event.asMap(), null)
    }.onFailure(onFailure)
  }

  fun emitPrisonerDifferenceEvent(
    offenderNo: String,
    differences: PrisonerDifferences,
  ) {
    PrisonerUpdatedDomainEvent(
      PrisonerUpdatedEvent(offenderNo, differences.keys.toList().sorted()),
      Instant.now(clock),
      diffProperties.host,
    ).publish {
      log.error("Failed to send event $UPDATED_EVENT_TYPE for offenderNo=$offenderNo, differences=$differences. Event will be retried")
      throw it
    }
  }

  fun emitPrisonerCreatedEvent(offenderNo: String) {
    PrisonerCreatedDomainEvent(PrisonerCreatedEvent(offenderNo), Instant.now(clock), diffProperties.host).publish {
      log.error("Failed to send event $CREATED_EVENT_TYPE for offenderNo=$offenderNo. Event will be retried")
      throw it
    }
  }

  fun emitPrisonerReceiveEvent(
    offenderNo: String,
    reason: PrisonerReceiveReason,
    prisonId: String,
    occurredAt: Instant? = null,
  ) {
    PrisonerReceivedDomainEvent(
      PrisonerReceivedEvent(offenderNo, reason, prisonId),
      occurredAt ?: Instant.now(clock),
      diffProperties.host,
    ).publish()
  }

  enum class PrisonerReceiveReason(val description: String) {
    NEW_ADMISSION("admission on new charges"),
    READMISSION("re-admission on an existing booking"),
    READMISSION_SWITCH_BOOKING("re-admission but switched to old booking"),
    TRANSFERRED("transfer from another prison"),
    RETURN_FROM_COURT("returned back to prison from court"),
    TEMPORARY_ABSENCE_RETURN("returned after a temporary absence"),
    POST_MERGE_ADMISSION("admission following an offender merge"),
  }

  enum class PrisonerReleaseReason(val description: String) {
    TEMPORARY_ABSENCE_RELEASE("released on temporary absence"),
    RELEASED_TO_HOSPITAL("released to a secure hospital"),
    RELEASED("released from prison"),
    SENT_TO_COURT("sent to court"),
    TRANSFERRED("transfer to another prison"),
  }

  fun emitPrisonerReleaseEvent(
    offenderNo: String,
    reason: PrisonerReleaseReason,
    prisonId: String,
  ) {
    PrisonerReleasedDomainEvent(
      PrisonerReleasedEvent(offenderNo, reason, prisonId),
      Instant.now(clock),
      diffProperties.host,
    ).publish()
  }

  fun emitPrisonerAlertsUpdatedEvent(
    offenderNo: String,
    bookingId: String?,
    alertsAdded: Set<String>,
    alertsRemoved: Set<String>,
  ) {
    PrisonerAlertsUpdatedDomainEvent(
      PrisonerAlertsUpdatedEvent(offenderNo, bookingId, alertsAdded, alertsRemoved),
      Instant.now(clock),
      diffProperties.host,
    ).publish()
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    const val UPDATED_EVENT_TYPE = "prisoner-offender-search.prisoner.updated"
    const val CREATED_EVENT_TYPE = "prisoner-offender-search.prisoner.created"
    const val PRISONER_RECEIVED_EVENT_TYPE = "prisoner-offender-search.prisoner.received"
    const val PRISONER_RELEASED_EVENT_TYPE = "prisoner-offender-search.prisoner.released"
    const val PRISONER_ALERTS_UPDATED_EVENT_TYPE = "prisoner-offender-search.prisoner.alerts-updated"
  }
}

interface PrisonerAdditionalInformation {
  val nomsNumber: String
}

open class PrisonerDomainEvent<T : PrisonerAdditionalInformation>(
  val additionalInformation: T,
  val occurredAt: String,
  val eventType: String,
  val version: Int,
  val description: String,
  val detailUrl: String,
  val personReference: PersonReference,
) {
  constructor(
    additionalInformation: T,
    occurredAt: Instant = Instant.now(),
    host: String,
    description: String,
    eventType: String,
    personReference: PersonReference,
  ) :
    this(
      additionalInformation = additionalInformation,
      occurredAt = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.of("Europe/London")).format(occurredAt),
      eventType = eventType,
      version = 1,
      description = description,
      detailUrl = ServletUriComponentsBuilder.fromUriString(host).path("/prisoner/{offenderNo}")
        .buildAndExpand(additionalInformation.nomsNumber).toUri().toString(),
      personReference = personReference,
    )
}

data class PersonReference(val identifiers: List<Identifier> = listOf()) {
  operator fun get(key: String) = identifiers.find { it.type == key }?.value
  fun findNomsNumber() = get(NOMS_NUMBER_TYPE)

  companion object {
    const val NOMS_NUMBER_TYPE = "NOMS"
    fun withNomsNumber(prisonNumber: String) = PersonReference(listOf(Identifier(NOMS_NUMBER_TYPE, prisonNumber)))
  }

  data class Identifier(val type: String, val value: String)
}

data class PrisonerUpdatedEvent(
  override val nomsNumber: String,
  val categoriesChanged: List<DiffCategory>,
) : PrisonerAdditionalInformation

class PrisonerUpdatedDomainEvent(additionalInformation: PrisonerUpdatedEvent, occurredAt: Instant, host: String) :
  PrisonerDomainEvent<PrisonerUpdatedEvent>(
    additionalInformation = additionalInformation,
    host = host,
    occurredAt = occurredAt,
    description = "A prisoner record has been updated",
    eventType = UPDATED_EVENT_TYPE,
    personReference = withNomsNumber(additionalInformation.nomsNumber),
  )

data class PrisonerCreatedEvent(override val nomsNumber: String) : PrisonerAdditionalInformation
class PrisonerCreatedDomainEvent(additionalInformation: PrisonerCreatedEvent, occurredAt: Instant, host: String) :
  PrisonerDomainEvent<PrisonerCreatedEvent>(
    additionalInformation = additionalInformation,
    host = host,
    occurredAt = occurredAt,
    description = "A prisoner record has been created",
    eventType = CREATED_EVENT_TYPE,
    personReference = withNomsNumber(additionalInformation.nomsNumber),
  )

data class PrisonerReceivedEvent(
  override val nomsNumber: String,
  val reason: PrisonerReceiveReason,
  val prisonId: String,
) : PrisonerAdditionalInformation

class PrisonerReceivedDomainEvent(additionalInformation: PrisonerReceivedEvent, occurredAt: Instant, host: String) :
  PrisonerDomainEvent<PrisonerReceivedEvent>(
    additionalInformation = additionalInformation,
    occurredAt = occurredAt,
    host = host,
    description = "A prisoner has been received into a prison with reason: ${additionalInformation.reason.description}",
    eventType = PRISONER_RECEIVED_EVENT_TYPE,
    withNomsNumber(additionalInformation.nomsNumber),
  )

data class PrisonerReleasedEvent(
  override val nomsNumber: String,
  val reason: PrisonerReleaseReason,
  val prisonId: String,
) : PrisonerAdditionalInformation

class PrisonerReleasedDomainEvent(additionalInformation: PrisonerReleasedEvent, occurredAt: Instant, host: String) :
  PrisonerDomainEvent<PrisonerReleasedEvent>(
    additionalInformation = additionalInformation,
    occurredAt = occurredAt,
    host = host,
    description = "A prisoner has been released from a prison with reason: ${additionalInformation.reason.description}",
    eventType = PRISONER_RELEASED_EVENT_TYPE,
    withNomsNumber(additionalInformation.nomsNumber),
  )

data class PrisonerAlertsUpdatedEvent(
  override val nomsNumber: String,
  val bookingId: String?,
  val alertsAdded: Set<String>,
  val alertsRemoved: Set<String>,
) : PrisonerAdditionalInformation

class PrisonerAlertsUpdatedDomainEvent(
  additionalInformation: PrisonerAlertsUpdatedEvent,
  occurredAt: Instant,
  host: String,
) :
  PrisonerDomainEvent<PrisonerAlertsUpdatedEvent>(
    additionalInformation = additionalInformation,
    occurredAt = occurredAt,
    host = host,
    description = "A prisoner had their alerts updated, added: ${additionalInformation.alertsAdded.size}, removed: ${additionalInformation.alertsRemoved.size}",
    eventType = PRISONER_ALERTS_UPDATED_EVENT_TYPE,
    withNomsNumber(additionalInformation.nomsNumber),
  )

fun <T : PrisonerAdditionalInformation> PrisonerDomainEvent<T>.asMap(): Map<String, String> = mutableMapOf(
  "occurredAt" to occurredAt,
  "eventType" to eventType,
  "version" to version.toString(),
  "description" to description,
  "detailUrl" to detailUrl,
).also { it.putAll(additionalInformation.asMap()) }

fun <T : PrisonerAdditionalInformation> T.asMap(): Map<String, String> =
  @Suppress("UNCHECKED_CAST")
  (this::class as KClass<T>).memberProperties
    .filter { it.get(this) != null }
    .associate { prop ->
      "additionalInformation.${prop.name}" to prop.get(this).toString()
    }
