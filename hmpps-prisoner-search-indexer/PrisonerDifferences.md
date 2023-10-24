# Prisoner Differences

The cronjob `hmpps-prisoner-search-indexer-full-index-refresh` runs every Monday, Wednesday and Friday evening at 19:00 UTC.
This calls the `/refresh-index/automated` endpoint to kick off a full index refresh.

The index refresh starts by calling Prison API to retrieve all the prisoners in NOMIS and add each one to the index 
queue.  For every message (prisoner) in the index queue it will then compare the OpenSearch index with NOMIS.
If there are no differences then no further action will be taken for that message.  If there are differences then:
1. An application insights telemetry event will be raised with summary details of the differences
2. Full details will be written to the `prisoner_differences` postgres database table
3. The latest prisoner details will then be written to the index
4. Events will be generated as a result of the changes to the prisoner
5. A slack alert will be generated with a link to the application insights difference query. Note that only one slack 
alert will be generated during a cronjob run even though there could be multiple events.

## Telemetry events
If a slack alert is generated then clicking `View` on the alert will show the differences in application insights.
Note that this will default to the `Custom` timestamp range so might not show all the differences in that cronjob run.
Alternatively a summary of the last index refresh can be found by running
```kusto
customEvents
| where cloud_RoleName == 'hmpps-prisoner-search-indexer'
| where name in ("DIFFERENCE_REPORTED", "DIFFERENCE_MISSING")
| where timestamp > ago(1d)
```

## Querying detailed prisoner differences
Calling the `/prisoner-differences` endpoint without any parameters will return all differences that have been found 
in the last 24 hours.  Additionally `from` and `to` parameters can be added to limit the results to specific time 
periods e.g. `from=2023-10-02T19:20:35.672232Z`.

The difference format is
```
    "differences": "[[<field 1>: <old value>, <new value>], [<field 2>: <old value>, <new value>]]"
```

## Investigating prisoner differences
It is important to investigate any differences in case we are missing a trigger in NOMIS or not listening to a new 
event.

### False positives
Sometimes the differences will be because of sentence wording changes e.g.
```
    "differences": "[[mostSeriousOffence: Encourage / assist in commission of eitherway offences believing one / more will be committed - Serious Crime Act 2007, Encourage / assist in commission of eitherway offences believing one / more will be committed]]",
```
which indicates that the sentence wording has been updated - this will then affect all prisoners have that most serious
offence.  We don't currently listen to sentence wording changes, instead we rely on the index refresh to correct the
wording.  Similarly, we don't listen to reference data changes so if a hospital name is changed this can
generate prisoner changes.

### Investigating events
When a difference is discovered for a prisoner this needs to be investigated.  Start by seeing if there have been any 
recent updates to their record in the indexer:
```kusto
let prisoner="<<prisoner number>>";
customEvents
| where cloud_RoleName == 'hmpps-prisoner-search-indexer'
| where (customDimensions.prisonerNumber == prisoner or customDimensions.["additionalInformation.nomsNumber"] == prisoner)
| where timestamp > ago(3d)
```
This will hopefully show, not only the recent difference run, but also any updates that have taken place in the last 
few days and the categories that have changed.  It will also show the events that triggered the changes, as well as
displaying the `bookingId`.
This information can then be used to cross-reference with prisoner events to see what events were raised at that time:
```
customEvents
| where cloud_RoleName == 'hmpps-prisoner-events'
| where (customDimensions.offenderIdDisplay == "<<prisoner number>>" or customDimensions.bookingId == <<booking id>>)
| where timestamp > ago(3d)
```
It is then a case of investigating the data in NOMIS to see what has happened and therefore why we don't have the
correct state for the prisoner - maybe there is additional data that changed that an event wasn't generated for?
