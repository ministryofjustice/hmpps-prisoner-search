@file:Suppress("PropertyName")

package uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.IndexListenerService
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.OffenderEventQueueService
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.OffenderEventQueueService.RequeueDestination
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.PersonReference

@Service
class DomainEventListener(
  private val objectMapper: ObjectMapper,
  private val indexListenerService: IndexListenerService,
  private val offenderEventQueueService: OffenderEventQueueService,
) {
  private companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    private val incentiveEvent = setOf(
      "incentives.iep-review.inserted",
      "incentives.iep-review.updated",
      "incentives.iep-review.deleted",
      "incentives.prisoner.next-review-date-changed",
    )
    private val restrictedPatientEvent = setOf(
      "restricted-patients.patient.added",
      "restricted-patients.patient.removed",
      "restricted-patients.patient.supporting-prison-changed",
    )
    private val alertEvent = setOf(
      "person.alert.created",
      "person.alert.updated",
      "person.alert.deleted",
      // "person.alert.inactive", this event is not handled as it only occurs alongside a "person.alert.updated" event
      // "person.alerts.changed", this event is not handled as it only occurs alongside another alert event
    )
  }

  @SqsListener("hmppsdomainqueue", factory = "hmppsQueueContainerFactoryProxy")
  fun processDomainEvent(requestJson: String?) {
    try {
      val (message, messageId, messageAttributes) = fromJson<Message>(requestJson)
      val eventType = messageAttributes.eventType.Value
      log.debug("Received message {} type {}", messageId, eventType)

      when (eventType) {
        in incentiveEvent -> indexListenerService.incentiveChange(fromJson(message), eventType)
        in restrictedPatientEvent -> indexListenerService.restrictedPatientChange(fromJson(message), eventType)
        in alertEvent -> indexListenerService.alertChange(fromJson(message), eventType)

        else -> log.warn("We received a message of event type {} which I really wasn't expecting", eventType)
      }
    } catch (e: Exception) {
      offenderEventQueueService.handleLockingFailureOrThrow(e, RequeueDestination.DOMAIN, requestJson)
    }
  }

  private inline fun <reified T> fromJson(message: String?): T = objectMapper.readValue(message, T::class.java)
}

data class IncentiveChangedMessage(
  val additionalInformation: IncentiveChangeAdditionalInformation,
  val eventType: String,
  val description: String,
)

data class IncentiveChangeAdditionalInformation(
  val nomsNumber: String,
  val id: Long,
)

data class RestrictedPatientMessage(
  val additionalInformation: RestrictedPatientAdditionalInformation,
  val eventType: String,
  val description: String,
)

data class RestrictedPatientAdditionalInformation(
  val prisonerNumber: String,
)

data class AlertEvent(
  val description: String?,
  val eventType: String,
  val additionalInformation: AlertAdditionalInformation,
  val personReference: PersonReference,
)

data class AlertAdditionalInformation(
  val alertUuid: String,
  val alertCode: String,
  val source: AlertSource,
)

/*
abstract class AlertBaseDomainEvent<T : AlertBaseAdditionalInformation> : DomainEvent {
  abstract override val eventType: String
  abstract override val additionalInformation: T
  abstract override val version: Int
  abstract override val description: String
  abstract override val occurredAt: ZonedDateTime
  override val detailUrl: String? = null
  override val personReference: PersonReference? = null
}

data class AlertDomainEvent<T : AlertBaseAdditionalInformation>(
  override val eventType: String,
  override val additionalInformation: T,
  override val version: Int = 1,
  override val description: String,
  override val occurredAt: ZonedDateTime,
  override val detailUrl: String? = null,
  override val personReference: PersonReference? = null,
) : AlertBaseDomainEvent<T>()


sealed interface DomainEventable {
  val type: DomainEventType
  fun detailPath(): String
  fun toDomainEvent(baseUrl: String): DomainEvent
}

sealed interface AlertEvent : DomainEventable {
  val alertUuid: UUID
  val prisonNumber: String
  val alertCode: String
  val occurredAt: LocalDateTime
  val source: Source
  override fun detailPath(): String = "/alerts/$alertUuid"
  override fun toDomainEvent(baseUrl: String): AlertDomainEvent<AlertAdditionalInformation> = AlertDomainEvent(
    eventType = type.eventType,
    additionalInformation = AlertAdditionalInformation(
      alertUuid = alertUuid,
      alertCode = alertCode,
      source = source,
    ),
    description = type.description,
    occurredAt = occurredAt.toZoneDateTime(),
    detailUrl = "$baseUrl${detailPath()}",
    personReference = PersonReference.withNomsNumber(prisonNumber),
  )
}

data class AlertCreatedEvent(
  override val alertUuid: UUID,
  override val prisonNumber: String,
  override val alertCode: String,
  override val occurredAt: LocalDateTime,
  override val source: Source,
  val createdBy: String,
) : AlertEvent {
  override val type: DomainEventType = ALERT_CREATED
}
*/

enum class AlertSource {
  DPS,
  NOMIS,
}

data class EventType(@JsonProperty("Value") val Value: String)
data class MessageAttributes(val eventType: EventType)
data class Message(val Message: String, val MessageId: String, val MessageAttributes: MessageAttributes)
