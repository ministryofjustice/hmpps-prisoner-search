# Restoring from Snapshots

See [OpenSearch Access](./OpenSearchAccess.md) for how to connect to the OpenSearch cluster.  This document assumes that
a port forward is in place.  The instructions below also assume that the current kubernetes cluster has been set:
```shell
kubectl config set-context --current --namespace="hmpps-prisoner-search-<<env>>"
```

To see what snapshots are available and have been registered for the repository run:
```shell
http http://localhost:19200/_snapshot
```
The `cs-automated-enc` is the AWS automated snapshot - `enc` shows that it is encrypted at rest.

## Automatic snapshots
AWS automatically snapshots the OpenSearch instances every hour into an S3 bucket that only that instance can see.

### Closing the indices
In order to restore from a snapshot all existing indices must either be closed or deleted.  Also it makes it easier if
the indexer is shutdown otherwise indices will be created if any events come through and the indexer will error if the
indices are then closed.
```shell
kubectl scale --replicas=0 deployment hmpps-prisoner-search-indexer
```
will shutdown the indexer.  Wait till the pods have then terminated before running any more commands.
```shell
http POST http://localhost:19200/_all/_close\?expand_wildcards\=all
```
will close all the indices - including the hidden by default `.opensearch` indices.  Running:
```shell
http http://localhost:19200/_cat/indices\?expand_wildcards\=all
```
should then hopefully show that all the indices are then closed.

### Restoring from automated snapshot
To see the list of snapshots run:
```shell
http http://localhost:19200/_snapshot/cs-automated-enc/_all
```
To then restore from a snapshot run:
```shell
http POST http://localhost:19200/_snapshot/cs-automated-enc/<<snapshot id>>/_restore
```
where the `<<snapshot id>>` is a snapshot identified from the list in the first step.
Progress can be monitored by running
```shell
http http://localhost:19200/_cat/recovery\?v
```
Once the snapshot restore has been completed ensure that all of the indices are then open and if necessary open them:
```shell
http POST http://localhost:19200/_all/_open\?expand_wildcards\=all
```
It is worth ensuring that all the indices are then also in the `green` state.

Finally scale the indexer back up - `4` for production and `2` normally for dev and pre-prod:
```shell
kubectl scale --replicas=4 deployment hmpps-prisoner-search-indexer
```

Note that the restoring from backup will not include any recent prisoner changes (there will also be a delay to generated domain events).  It would then be necessary to either
run a full re-index or alternatively run an index refresh to ensure that the newly restrored index contains all the 
same data as NOMIS. See [Index Maintenance](./IndexMaintenance.md) and [Prisoner Differences](./PrisonerDifferences.md).

## Snapshot cronjobs
There are two kubernetes snapshot cronjobs:
1. The `hmpps-prisoner-search-indexer-opensearch-snapshot` runs early every morning to take a snapshot
of the whole cluster and store in a S3 bucket.
2. The `hmpps-prisoner-search-indexer-opensearch-restore` runs every four hours in pre-production only.  This is so that
the pre-production index can be kept in sync with the NOMIS database.  The job checks to see if there is a newer version
of the NOMIS database since the last restore and if so then does another restore.  Pre-production has access to the
production snapshot s3 bucket and uses that to restore the latest production snapshot created by step 1.

### Manually running the create snapshot cronjob
```shell
kubectl create job --from=cronjob/hmpps-prisoner-search-indexer-opensearch-snapshot hmpps-prisoner-search-indexer-opensearch-snapshot-<<user>>
```
will trigger the job to create a snapshot called latest.
Job progress can then be seen by running `kubectl logs -f` on the newly created pod.

### Manually running the restore snapshot cronjob
The restore cronjob script only runs if there is a newer NOMIS database so we need to override the configuration to
ensure to force the run. We do that by using `jq` to amend the json and adding in the `FORCE_RUN=true` parameter.

In dev and production there is only one snapshot repository so
```shell
kubectl create job --dry-run=client --from=cronjob/hmpps-prisoner-search-indexer-opensearch-restore hmpps-prisoner-search-indexer-opensearch-restore-<<user>> -o "json" \
  | jq ".spec.template.spec.containers[0].env += [{ \"name\": \"FORCE_RUN\", \"value\": \"true\"}]" | kubectl apply -f -
```
will trigger the job to restore the snapshot called latest.
Job progress can then be seen by running `kubectl logs -f` on the newly created pod.

The default for the cronjob in pre-production is to restore from production.  If that is required then the command above
will suffice.  However, if it required to restore from the previous pre-production snapshot then we need to clear the
`NAMESPACE_OVERRIDE` environment variable so that it doesn't try to restore from production instead.
```shell
kubectl create job --dry-run=client --from=cronjob/hmpps-prisoner-search-indexer-opensearch-restore hmpps-prisoner-search-indexer-opensearch-restore-<<user>> -o "json" \
  | jq "(.spec.template.spec.containers[0].env += [{ \"name\": \"FORCE_RUN\", \"value\": \"true\"}]) | (.spec.template.spec.containers[0].env[] | select(.name==\"NAMESPACE_OVERRIDE\").value) |= \"\"" \
  | kubectl apply -f -
```

The last successful restore information is stored in a `restore-status` index.  To find out when the last restore ran:
```
http http://localhost:19200/restore-status/_doc/1
```

### Restore from a manual snapshot (if both indexes have become corrupt/empty)
It would normally be preferable to restore from an automated snapshot since they are taken every hour, whereas the
snapshot cronjob is only run once a day.  However it may be that automated snapshots are not available.

If the indexes are broken then it is likely that the snapshot repository information has all been removed too.  Running
```shell
http http://localhost:19200/_snapshot
```
will show the available snapshots.  If there aren't any snapshots available then the manual s3 bucket will need to be
added.  Using the two `os-snapshot-xxx` secrets in the namespace run:
```shell
http POST http://localhost:19200/_snapshot/<<namespace>> type="s3" settings:="{
    \"bucket\": \"<<bucket_name within os-snapshot-bucket secret>>\",
    \"region\": \"eu-west-2\",
    \"role_arn\": \"<<secret snapshot_role_arn within os-snapshot-role secret>>\",
    \"base_path\": \"<<NAMESPACE>>\",
    \"readonly\": \"true\"
    }"
```
to add all the available snapshots in read only mode.  This is advisable if attempting to restore a snapshot from
production to pre-production.
Then follow the steps in [Restoring from automated snapshot](#restoring-from-automated-snapshot) above.

### To take a manual snapshot, perform the following steps:

You can't take a snapshot if one is currently in progress. To check, run the following command:

```shell
http 'http://localhost:19200/_snapshot/_status
```

Run the following command to take a manual snapshot:
```shell
http PUT 'http://localhost:19200/_snapshot/<<NAMESPACE>>/snapshot-name
```
You can now use the restore commands above to restore the snapshot if needed.

### To remove a snapshot
```shell
http DELETE 'http://localhost:19200/_snapshot/<<NAMESPACE>>/snapshot-name
```
