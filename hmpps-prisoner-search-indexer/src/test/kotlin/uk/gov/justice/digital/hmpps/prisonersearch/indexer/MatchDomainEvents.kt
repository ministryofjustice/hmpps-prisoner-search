package uk.gov.justice.digital.hmpps.prisonersearch.indexer

import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import java.io.File
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/* Use this query and export it to generate the csv file:

AppEvents
| where  AppRoleName == 'hmpps-prisoner-search-indexer'
  // and TimeGenerated between (todatetime('2025-02-27T12:00:00.0Z')..12h)
  and (Name startswith_cs "RED_SIMULATE" or Name startswith "prisoner-offender-search.")
   // and Properties contains "A2560EC"
| project TimeGenerated,Name,Properties,OperationId, OperationName
| extend
  eventType=Properties.["eventType"],
  prisonerNumber = coalesce( Properties.prisonerNumber, Properties.["additionalInformation.nomsNumber"]),
  categoriesChanged=Properties.["additionalInformation.categoriesChanged"],
  reason=Properties.["additionalInformation.reason"],
  convictedStatus=Properties.["additionalInformation.convictedStatus"],
  prisonId=Properties.["additionalInformation.prisonId"],
  alertsAdded=Properties.["additionalInformation.alertsAdded"],
  alertsRemoved=Properties.["additionalInformation.alertsRemoved"]
| project TimeGenerated,Name,categoriesChanged,prisonId,reason,convictedStatus,alertsAdded,alertsRemoved,OperationId, OperationName, eventType, prisonerNumber
| order by TimeGenerated asc
 */

// Define the data class
data class Event(
  val timeGenerated: String,
  val name: String,
  val categoriesChanged: List<String>,
  val operationId: String,
  val operationName: String,
  val eventType: String,
  val prisonerNumber: String,
  val prisonId: String,
  val reason: String,
  val convictedStatus: String,
  val alertsAdded: String,
  val alertsRemoved: String,
)

fun main() {
  // Path to the CSV file
  val csvFilePath = "/Users/steve.rendell/git/hmpps-prisoner-search/hmpps-prisoner-search-indexer/events-mar4am.csv"

  // Read the CSV file and map each row to a Person object
  val map = csvReader()
    .readAllWithHeader(File(csvFilePath))
    .map { row ->
      Event(
        timeGenerated = row["TimeGenerated [Local Time]"]!!,
        name = row["Name"]!!,
        categoriesChanged = row["categoriesChanged"]!!.trim('[',']'). split(",").map { it.trim() },
        operationId = row["OperationId"]!!,
        operationName = row["OperationName"]!!,
        eventType = row["eventType"]!!,
        prisonerNumber = row["prisonerNumber"]!!,
        prisonId = row["prisonId"]!!,
        reason = row["reason"]!!,
        convictedStatus = row["convictedStatus"]!!,
        alertsAdded = row["alertsAdded"]!!,
        alertsRemoved = row["alertsRemoved"]!!,
      )
    }
  val events: MutableList<Event> = map
    .distinctBy {
      it.prisonerNumber + it.name + it.categoriesChanged + it.eventType + it.prisonId + it.reason + it.convictedStatus + it.alertsAdded + it.alertsRemoved + it.timeGenerated.substring(
        0,
        20,
      ) // truncate millis, eg: '26/02/2025, 11:58:34.221'
    }
    .toMutableList()

  do {
    println("Loop start, event count ${events.size}")
    val toBeRemoved = mutableListOf<Event>()
    events.groupBy { it.prisonerNumber + it.eventType }
      .forEach { (_prisonerNumberAndEventType, eventsForGroup) ->
        eventsForGroup
          .find { it.name.startsWith("prisoner-") }
          ?.let { domainEvent ->

            val startSize = toBeRemoved.size

            if (domainEvent.eventType == "prisoner-offender-search.prisoner.updated" && domainEvent.categoriesChanged.containsOnly("INCENTIVE_LEVEL")) {
              // Incentives only
              val red = eventsForGroup.find { it.name.startsWith("RED") && it.categoriesChanged.containsOnly("INCENTIVE_LEVEL") && closeInTime(it, domainEvent) }
              if (red != null) {
                println("Found incentives only match:\n$domainEvent\n$red")
                toBeRemoved.add(domainEvent)
                toBeRemoved.add(red)
              }
            } else if (domainEvent.eventType == "prisoner-offender-search.prisoner.updated" && domainEvent.categoriesChanged.contains("INCENTIVE_LEVEL")) {
              // Incentives, or restricted patients plus others: there could be 2 RED events
              val red1 = eventsForGroup.find { it.name == ("RED_SIMULATE_PRISONER_DIFFERENCE_EVENT") && it.categoriesChanged.contains("INCENTIVE_LEVEL") && closeInTime(it, domainEvent) }
              val red2 = eventsForGroup.find { it.name == ("RED_SIMULATE_PRISONER_DIFFERENCE_EVENT") && !it.categoriesChanged.contains("INCENTIVE_LEVEL") && closeInTime(it, domainEvent) }
              if (red1 != null && red2 != null) {
                println("Found incentives match:\n$domainEvent\n$red1\n$red2")
                toBeRemoved.add(domainEvent)
                toBeRemoved.add(red1)
                toBeRemoved.add(red2)
              }
            } else if (domainEvent.eventType == "prisoner-offender-search.prisoner.updated" && domainEvent.categoriesChanged.contains("RESTRICTED_PATIENT")) {
              // can get RED 'LOCATION, RESTRICTED_PATIENT' + just 'LOCATION' to 1 domain event 'LOCATION, RESTRICTED_PATIENT'
              val red1 = eventsForGroup.find { it.name == ("RED_SIMULATE_PRISONER_DIFFERENCE_EVENT") && it.categoriesChanged.contains("RESTRICTED_PATIENT") && closeInTime(it, domainEvent) }
              val red2 = eventsForGroup.find {
                it.name == ("RED_SIMULATE_PRISONER_DIFFERENCE_EVENT") &&
                !it.categoriesChanged.contains("RESTRICTED_PATIENT") &&
                closeInTime(it, domainEvent)
              }
              if (red1 != null && red2 != null && closeInTime(red2, domainEvent)) {
                println("Found RP match:\n$domainEvent\n$red1\n$red2")
                toBeRemoved.add(domainEvent)
                toBeRemoved.add(red1)
                toBeRemoved.add(red2)
              }

              // Also can get duplicate RED events for alerts, see 942433069bd662fe723419fa2ce219b9 on 26 feb 06:59

              // Can get duplicate old events for sentence, see 'a7e6fe1b46772d2b6341ee4e652a5dc1','06dfd5cbf7ec2ba3f0f44f48df85066d' on 26 feb 09:21
            }
            val specialCasesDidNotApply = startSize == toBeRemoved.size

            if (specialCasesDidNotApply) {
              val red = eventsForGroup.find { it.name.startsWith("RED") && closeInTime(it, domainEvent) }
              if (red != null &&
                !when {
                  red.name == "RED_SIMULATE_PRISONER_DIFFERENCE_EVENT" ->
                    (!equalInAnyOrder(red.categoriesChanged, domainEvent.categoriesChanged)).also { if (it) println("Found match BUT categoriesChanged differ (${red.categoriesChanged})(${domainEvent.categoriesChanged}):\n$domainEvent\n$red") }

                  red.name == "RED_SIMULATE_MOVEMENT_RECEIVE_EVENT" ->
                    (red.reason != domainEvent.reason || red.prisonId != domainEvent.prisonId).also { if (it) println("Found match BUT movement receive reason or prisonId differs:\n$domainEvent\n$red") }

                  red.name == "RED_SIMULATE_MOVEMENT_RELEASE_EVENT" ->
                    (red.reason != domainEvent.reason || red.prisonId != domainEvent.prisonId).also { if (it) println("Found match BUT movement release reason or prisonId differs:\n$domainEvent\n$red") }

                  red.name == "RED_SIMULATE_CONVICTED_STATUS_CHANGED_EVENT" ->
                    (red.convictedStatus != domainEvent.convictedStatus).also { if (it) println("Found match BUT prisoner created reason or prisonId differs:\n$domainEvent\n$red") }

                  red.name == "RED_SIMULATE_ALERT_EVENT" ->
                    (red.alertsAdded != domainEvent.alertsAdded || red.alertsRemoved != domainEvent.alertsRemoved).also { if (it) println("Found match BUT alert numbers differ:\n$domainEvent\n$red") }

                  red.name == "RED_SIMULATE_PRISONER_CREATED_EVENT" || red.name == "RED_SIMULATE_PRISONER_REMOVED_EVENT" -> false

                  else -> {
                    println("Unrecognised code: ${red.name}")
                    true
                  }
                }
              ) {
                toBeRemoved.add(domainEvent)
                toBeRemoved.add(red)
              }
            }
          }
      }
    println("Removing ${toBeRemoved.size} events")
    events.removeAll(toBeRemoved)
  }
  while (toBeRemoved.isNotEmpty())
  println("\nEvents remaining unmatched:")
  events.forEach { println(it) }
}

private fun equalInAnyOrder(
  list1: List<String>,
  list2: List<String>,
): Boolean = list1.size == list2.size && list1.containsAll(list2)

val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy, HH:mm:ss.SSS")

private fun closeInTime(red: Event, domainEvent: Event): Boolean {
  val text1 = red.timeGenerated
  val text2 = domainEvent.timeGenerated
  return closeInTime2(text1, text2)
}

private fun closeInTime2(text1: String, text2: String): Boolean {
  val firstTimestamp = LocalDateTime.parse(text1, formatter)
  val secondTimestamp = LocalDateTime.parse(text2, formatter)
  val differenceInSeconds = Duration.between(firstTimestamp, secondTimestamp).seconds
  return abs(differenceInSeconds) <= 6
}

fun List<String>.containsOnly(value: String): Boolean = this.size == 1 && this[0] == value
