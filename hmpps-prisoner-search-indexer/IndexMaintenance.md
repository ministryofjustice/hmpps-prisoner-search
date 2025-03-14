# Index Maintenance

## Index rebuilds
When we are ready to rebuild the index it is transitioned into a `BUILDING` state of `true`.

```shell
http PUT /maintain-index/build
```

The entire NOMIS prisoner base is retrieved and over several hours the index is fully refreshed.  To speed up this
process the number of active pods can be increased e.g.
```shell
kubectl scale --replicas=8 deployment hmpps-prisoner-search-indexer
```
Note that if rebuilding at peak times with more than `4` pods it would be advisable to also increase the number of 
Prison API pods as well.

Index progress can be viewed by looking at the `/info` endpoint e.g.
```json
"index-queue-backlog": "0",
"index-size": {
"size": 754090
}
```
When the backlog has reached 0 then all indexing messages have been processed. Check the dead letter queue is empty
by looking at the `/health` endpoint:
```json
"index-health": {
    "details": {
        "dlqName": "syscon-devs-prod-hmpps_prisoner_search_index_queue_dl",
        "dlqStatus": "UP",
        "messagesInFlight": "0",
        "messagesOnDlq": "0",
        "messagesOnQueue": "0",
        "queueName": "syscon-devs-prod-hmpps_prisoner_search_index_queue"
    },
    "status": "UP"
},
```

Once the index has finished, if there are no errors then the
[check index complete cronjob](#check-index-complete-cronjob) will mark the index as complete.  The `/info` endpoint should then show this:
```json
"index-status": {
    "currentIndexEndBuildTime": "2023-10-13T09:54:11",
    "currentIndexStartBuildTime": "2023-10-13T07:23:09",
    "currentIndexState": "COMPLETED",
},
```

If the index build fails - there are messages left on the index dead letter queue (DLQ) - then the index will 
remain in a BUILDING state until the DLQ is empty. It may take user intervention to clear the DLQ if some messages are genuinely
unprocessable (rather than just failed due to e.g. network issues). See [Useful Queries](./UsefulQueries.md) for 
how to investigate the issues further.

## Index switch
The state of the index is itself held in OpenSearch under the `prisoner-index-status` index with a single `STATUS`
"document" and exposed in the `/info` endpoint.

## Check index complete cronjob
There is a Kubernetes CronJob called 
[hmpps-prisoner-search-indexer-check-index-complete](./helm_deploy/hmpps-prisoner-search-indexer/templates/check-indexing-complete.yaml)
which runs on a schedule 
to perform the following tasks:
* Checks if an index build has completed and if so then marks the build as complete

The CronJob calls the endpoint `/maintain-index/check-complete` which is not secured by Spring Security.
To prevent external calls to the endpoint it has been secured in the ingress instead.

## Index investigations
There are a number of endpoints that can be used to investigate potential issues with the index

### Index comparison by prisoner numbers
The `/compare-index/ids` will retrieve a list of prisoner numbers in NOMIS and compare them with the list in the index.
This will then be written to an application insights `customEvent` called `COMPARE_INDEX_IDS`.  Note that the comparison
is only at prisoner number level - for a more detailed comparison of each prisoner see 
[Prisoner Differences](./PrisonerDifferences.md).

### Index comparison by individual prisoner
The `/compare-index/prisoner/{prisonerNumber}` endpoint can be used to compare a NOMIS record with the record in the
OpenSearch index.  If differences are found then please see [Prisoner Differences](./PrisonerDifferences.md) for 
how to investigate the events.

### Re-indexing individual prisoners
The `/maintain-index/index-prisoner/{prisonerNumber}` endpoint can be used to re-index an individual prisoner whose
record in case there are changes that have happened where an event hasn't been generated.  One example of this is
manually removing a prisoner from the restricted patients database, or amending the supporting prison of the
restricted patient manually.
