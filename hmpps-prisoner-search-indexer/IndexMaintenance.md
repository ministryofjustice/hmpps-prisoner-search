# Index Maintenance

## Index rebuilds
This service maintains two indexes `prisoner-search-green` and `prisoner-search-blue` known in the code
as `GREEN` and `BLUE`.

In normal running one of these indexes will be "active" while the other is dormant and not in use.

When we are ready to rebuild the index the "other" non-active index is transitioned into a `BUILDING` state of `true`.

```shell
http PUT /maintain-index/build
```

The entire NOMIS prisoner base is retrieved and over several hours the other index is fully populated.  To speed up this
process the number of active pods can be increased e.g.
```shell
kubectl scale --replicas=8 deployment hmpps-prisoner-search-indexer
```
Note that if rebuilding at peak times with more than `4` pods it would be advisable to also increase the number of 
Prison API pods as well.

Index progress can be viewed by looking at the `/info` endpoint e.g.
```json
{
  "index-queue-backlog": "0",
  "index-size": {
    "BLUE": 754090,
    "GREEN": 754090
  }
}
```

Once the index has finished, if there are no errors then the
[check index complete cronjob](#check-index-complete-cronjob) will mark the index as complete and switch to the new
index.

If the index build fails - there are messages left on the index dead letter queue (DLQ) - then the new index will 
remain inactive until the DLQ is empty. It may take user intervention to clear the DLQ if some messages are genuinely
unprocessable (rather than just failed due to e.g. network issues). See [Useful Queries](./UsefulQueries.md) for 
how to investigate the issues further.

### Index switch
The state of each index is itself held in OpenSearch under the `prisoner-index-status` index with a single `STATUS`
"document" and exposed in the `/info` endpoint.
When the GREEN / BLUE indices switch there are actually two changes:
* The document in `prisoner-index-status` to indicate which index is currently active
* The OpenSearch alias `prisoner` is switched to point at the active index. This means external clients can safely 
use the `prisoner` index without any knowledge of the GREEN / BLUE indexes.

Indexes can be switched without rebuilding, if they are both marked as "inProgress": false and "inError":false
```shell
    http PUT /maintain-index/switch
```

### Check index complete cronjob
There is a Kubernetes CronJob called 
[hmpps-prisoner-search-indexer-check-index-complete](./helm_deploy/hmpps-prisoner-search-indexer/templates/check-indexing-complete.yaml)
which runs on a schedule 
to perform the following tasks:
* Checks if an index build has completed and if so then marks the build as complete (which switches the search to the
new index)
* A threshold is set for each environment (in the helm values file) and the index will not be marked as complete
until this threshold is met. This is to prevent switching to an index that does not look correct and will
require a manual intervention to complete the index build (e.g. calling the `/maintain-index/mark-complete` endpoint
manually).

The CronJob calls the endpoint `/maintain-index/check-complete` which is not secured by Spring Security.
To prevent external calls to the endpoint it has been secured in the ingress instead.
