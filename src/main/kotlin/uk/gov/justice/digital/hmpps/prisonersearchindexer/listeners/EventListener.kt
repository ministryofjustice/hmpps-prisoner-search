package uk.gov.justice.digital.hmpps.indexer.listeners

import com.google.gson.Gson
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearchindexer.services.IndexService

@Service
class EventListener(
  private val gson: Gson,
  private val indexService: IndexService,
) {
  private companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
//
//  @SqsListener("event", factory = "hmppsQueueContainerFactoryProxy")
//  fun processOffenderEvent(requestJson: String?) {
//    val (message, _, messageAttributes) = gson.fromJson(requestJson, Message::class.java)
//
//    when (val eventType = messageAttributes.eventType.Value) {
//      in updateEvents -> indexService.updatePrisoner(gson.fromJson(message, OffenderChangedEvent::class.java).prisonerNumber)
//      else -> log.error("We received a message of event type {} which I really wasn't expecting", eventType)
//    }
//  }
}

private val updateEvents = listOf("OFFENDER_CHANGED", "OFFENDER_REGISTRATION_CHANGED", "OFFENDER_REGISTRATION_DEREGISTERED", "OFFENDER_REGISTRATION_DELETED", "SENTENCE_CHANGED", "CONVICTION_CHANGED")

data class EventType(val Value: String)
data class MessageAttributes(val eventType: EventType)
data class Message(val Message: String, val MessageId: String, val MessageAttributes: MessageAttributes)
data class OffenderChangedEvent(val prisonerNumber: String)
