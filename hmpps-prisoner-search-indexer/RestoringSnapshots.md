# Restoring from Snapshots

## Snapshot cronjobs
There are two kubernetes cronjobs
1. A scheduled job runs at 2.30am each day to take a snapshot of the whole cluster.
2. A scheduled job runs every four hours in pre-production only.  This checks to see if there is a newer version of
the NOMIS database since the last restore and if so then does another restore.  Pre-production has access to the
production snapshot s3 bucket and uses that to restore the latest production snapshot created by step 1.

#### Manually running the create snapshot cronjob
```shell
kubectl create job --from=cronjob/prisoner-offender-search-elasticsearch-snapshot prisoner-offender-search-elasticsearch-snapshot-<user>
```
will trigger the job to create a snapshot called latest.
Job progress can then be seen by running `kubectl logs -f` on the newly created pod.

#### Manually running the restore snapshot cronjob
The restore cronjob script only runs if there is a newer NOMIS database so we need to override the configuration to ensure to force the run.
We do that by using `jq` to amend the json and adding in the `FORCE_RUN=true` parameter.

In dev and production there is only one snapshot repository so
```shell
kubectl create job --dry-run=client --from=cronjob/prisoner-offender-search-elasticsearch-restore prisoner-offender-search-elasticsearch-restore-<user> -o "json" | jq ".spec.template.spec.containers[0].env += [{ \"name\": \"FORCE_RUN\", \"value\": \"true\"}]" | kubectl apply -f -
```
will trigger the job to restore the snapshot called latest.
Job progress can then be seen by running `kubectl logs -f` on the newly created pod.

The default for the cronjob in pre-production is to restore from production.  If that is required then the command above
will suffice.  However, if it required to restore from the previous pre-production snapshot then we need to clear the
`NAMESPACE_OVERRIDE` environment variable so that it doesn't try to restore from production instead.
```shell
kubectl create job --dry-run=client --from=cronjob/prisoner-offender-search-elasticsearch-restore prisoner-offender-search-elasticsearch-restore-pgp -o "json" | jq "(.spec.template.spec.containers[0].env += [{ \"name\": \"FORCE_RUN\", \"value\": \"true\"}]) | (.spec.template.spec.containers[0].env[] | select(.name==\"NAMESPACE_OVERRIDE\").value) |= \"\"" | kubectl apply -f -
```

The last successful restore information is stored in a `restore-status` index.  To find out when the last restore ran:
```
http GET http://localhost:9200/restore-status/_doc/1
```

### Restore from a snapshot (if both indexes have become corrupt/empty)

If we are restoring from the snapshot it means that the current index and other index are broken, we need to delete them to be able to restore from the snapshot.
At 2.30am we have a scheduled job that takes the snapshot of the whole cluster which is called `latest` and this should be restored.

1. To restore we need to port-forward to the es instance (replace NAMESPACE with the affected namespace)
   ```shell
   kubectl -n <NAMESPACE> port-forward $(kubectl -n <NAMESPACE> get pods | grep aws-es-proxy-cloud-platform | grep Running | head -1 | awk '{print $1}') 9200:9200
   ```
2. If the indexes are broken then it is likely that the snapshot repository information has all been removed too.  Running
   ```shell
   http http://localhost:9200/_snapshot
   ```
   will show the available snapshots.  If the snapshots aren't available then running
   ```shell
   http POST http://localhost:9200/_snapshot/$NAMESPACE type="s3" settings:="{
             \"bucket\": \"${secret bucket_name within es-snapshot-bucket}\",
             \"region\": \"eu-west-2\",
             \"role_arn\": \"${secret snapshot_role_arn within es-snapshot-role}\",
             \"base_path\": \"$NAMESPACE\",
             \"readonly\": \"true\"
             }"
   ```
   will add all the available snapshots in read only mode.

3. Delete the current indexes
   ```shell
   http DELETE http://localhost:9200/_all
   ```
4. Check that the indices have all been removed
   ```shell
   http http://localhost:9200/_cat/indices
   ```
   If you wait any length of time between the delete and restore then the `.kibana` ones might get recreated,
   you'll need to delete them again otherwise the restore will fail.
   If necessary you might need to do steps 2 and 4 at the same time so that it doesn't get recreated inbetween.
5. Then we can start the restore (SNAPSHOT_NAME for the overnight snapshot is `latest`)
   ```shell
   http POST 'http://localhost:9200/_snapshot/<NAMESPACE>/<SNAPSHOT_NAME>/_restore' include_global_state=true
   ```

   The `include_global_state: true` is set true so that we copy the global state of the cluster snapshot over. The default for restoring,
   however, is `include_global_state: false`. If only restoring a single index, it could be bad to overwrite the global state but as we are
   restoring the full cluster we set it to true.

6. The indices will be yellow until they are all restored - again check they are completed with
   ```shell
   http http://localhost:9200/_cat/indices
   ```
#### To view the state of the indexes while restoring from a snapshot

##### Cluster health

`http 'http://localhost:9200/_cluster/health'`

The cluster health status is: green, yellow or red. On the shard level, a red status indicates that the specific shard is not allocated in the cluster, yellow means that the primary shard is allocated but replicas are not, and green means that all shards are allocated. The index level status is controlled by the worst shard status. The cluster status is controlled by the worst index status.

##### Shards
`http 'http://localhost:9200/_cat/shards'`

The shards command is the detailed view of what nodes contain which shards. It will tell you if it’s a primary or replica, the number of docs, the bytes it takes on disk, and the node where it’s located.

##### Recovery
`http 'http://localhost:9200/_cat/recovery'`

Returns information about ongoing and completed shard recoveries

#### To take a manual snapshot, perform the following steps:

1. You can't take a snapshot if one is currently in progress. To check, run the following command:

   `http 'http://localhost:9200/_snapshot/_status'`
2. Run the following command to take a manual snapshot:

   `http PUT 'http://localhost:9200/_snapshot/<NAMESPACE>/snapshot-name'`

you can now use the restore commands above to restore the snapshot if needed

##### To remove a snapshot
`http DELETE 'http://localhost:9200/_snapshot/<NAMESPACE>/snapshot-name'`

#### Other command which will help when looking at restoring a snapshot

To see all snapshot repositories, run the following command (normally there will only be one, as we have one per namespace):

`http 'http://localhost:9200/_snapshot?pretty'`

In the pre-production namespace there will be a pre-production snapshot repository and also the production repository.
The latter is used for the restore and should be set to `readonly` so that it can't be overwritten with
pre-production data.

To see all snapshots for the namespace run the following command:

`http 'http://localhost:9200/_snapshot/<NAMESPACE>/_all?pretty'`
