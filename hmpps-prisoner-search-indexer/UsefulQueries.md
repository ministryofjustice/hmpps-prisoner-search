# Useful Application Insights Queries

## Custom events
Most of the interesting information as to what is happening in the indexer is written as `customEvents`:
```kusto
customEvents
| where cloud_RoleName == "hmpps-prisoner-search-indexer"
| order by timestamp desc
```
There are 3 main events (`name` field):
* `PRISONER_UPDATED` if a prisoner has changed
* `PRISONER_OPENSEARCH_NO_CHANGE` if the OpenSearch index hash already contains the changes
* `PRISONER_DATABASE_NO_CHANGE` if the OpenSearch index is different but the database hash is the same.
We first check OpenSearch to see if the prisoner has changed, but since does not guarantee consistency it could 
be that a separate thread has already written a new version of the prisoner and we haven't been able to read it yet.
We therefore store the prisoner hash in a postgres database too, which since it is
[ACID compliant](https://en.wikipedia.org/wiki/ACID) will guarantee that we only perform an update of the prisoner
if it has changed.  This is then used to ensure that we don't generate a new domain event when there have been no
additional changes to the prisoner.

The `customDimensions.event` will provide information as to what event triggered the change.
* `REFRESH` indicates the trigger was the three times a week index refresh - see
[Prisoner Differences](./PrisonerDifferences.md).
* `MAINTAIN` indicates that it was a manual call to the maintain index endpoint - see
[Index Maintenance](./IndexMaintenance.md).
* All other values will be either a domain event or a prison event.

## Interesting exceptions

```kusto
exceptions
| where cloud_RoleName == "hmpps-prisoner-search-indexer"
| where operation_Name != "GET /health"
| where customDimensions !contains "health"
| where details !contains "HealthCheck"
| order by timestamp desc
```
To find out more about a particular exception, grab the `operation_Id` and then, for example, use it to view
dependencies:
```kusto
dependencies
| where operation_Id == "<<operation id>>"
```

## Indexing dependencies
Since the indexing happens on a separate queue we can use that information to find out, for example, all
the index queries to Prison API:
```kusto
dependencies
| where cloud_RoleName == "hmpps-prisoner-search-indexer"
| where operation_Name == "syscon-devs-hmpps_prisoner_search_index_queue"
| where target == "prison-api-dev.prison.service.justice.gov.uk"
```

## Investigating queue processing
We listen to both prison and domain events.  For the queue listener requests the `url` will be blank, so we use that 
to show only those requests:
```kusto
requests
| where cloud_RoleName == 'hmpps-prisoner-search-indexer'
| where url == ""
| where timestamp > ago(1d)
| summarize totalCount=sum(itemCount) by bin(timestamp, 5m), name
| render timechart
```
This is useful to see if there is an issue with one of the queues.
