# Alerts

## Index size check
The cronjob 
[hmpps-prisoner-search-indexer-compare-index-size](./helm_deploy/hmpps-prisoner-search-indexer/templates/compare-index-size-cronjob.yaml)
runs every 10 minutes in production and retrieves the current number of people in NOMIS and also in the OpenSearch
index.  The results are output to a `customEvent` called `COMPARE_INDEX_SIZE`.
The [index size check](https://github.com/ministryofjustice/nomis-api-terraform-application-insights/blob/main/modules/hmpps-prisoner-search-indexer/index-size-check.tf)
application insights alert then checks the two sizes and alerts if they are different.  Clicking the `View` button
on the alert will then display the query in application insights so that further investigation can take place.

### NOMIS size far greater
If the index size is far less than the NOMIS size then this would indicate the index has become corrupt in some way,
or that there are issues with OpenSearch.  In [OpenSearch Access](./OpenSearchAccess.md) there are instructions
to view the AWS console to see if there issues, as well as instructions to view all the indices.  If necessary
there are also instructions in [Restoring Snapshots](./RestoringSnapshots.md) to restore the indices from a 
snapshot before the corruption took place.

### NOMIS size close to index size
This would indicate that we haven't processed some recent prisoner events.  It could be that there are messages on the
dead letter queue that can't be processed or there is an issue with the queues that means that we are not receiving the
events. See [Useful Queries](./UsefulQueries.md) to check for exceptions and also whether there is an issue with one of 
the listeners.

### Index size greater
This would indicate that a person has been removed from NOMIS and we have not received the deletion event (if raised).
See [Index Maintenance](./IndexMaintenance.md) in order to find out which prisoners have been removed and how to then
remove them from the index as well.  It would also require investigation to find out why we didn't receive the deletion
event.

## Prisoner differences
The cronjob
[hmpps-prisoner-search-indexer-full-index-refresh](./helm_deploy/hmpps-prisoner-search-indexer/templates/full-index-refresh-cronjob.yaml)
runs 3 times a week in production to compare the index with NOMIS for each prisoner.
Any differences are output to `customEvent` called `DIFFERENCE_MISSING` and `DIFFERENCE_REPORTED`.
The [prisoner differences check](https://github.com/ministryofjustice/nomis-api-terraform-application-insights/blob/main/modules/hmpps-prisoner-search-indexer/prisoner-differences-check.tf)
application insights alert fires if any differences are found.  See [Prisoner Differences](./PrisonerDifferences.md)
for more information.

## Queue alerts
The standard 
[HMPPS queue alerts](https://github.com/ministryofjustice/hmpps-helm-charts/blob/main/charts/generic-prometheus-alerts/templates/aws-sqs-alerts.yaml)
for oldest messages and number of messages are in place for both the domain and prison
queues.  This ensures that the indexer is processing messages regularly.

Furthermore, there are custom alerts for the index queue as we want separate limits for the index queue.  This is because
when we are running an index refresh or index build we will put over 700,000 messages on the index queue and gradually
then process the messages over the next few hours.  The alert will then fire instead if messages aren't processed
from the queue in 3 hours. See
[index queue prometheus rules](./helm_deploy/hmpps-prisoner-search-indexer/templates/index-queues-prometheus-rules.yaml)
for the custom rule definition.

