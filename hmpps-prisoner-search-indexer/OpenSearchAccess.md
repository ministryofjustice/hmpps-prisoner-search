# OpenSearch access

## AWS Console
Various cluster information can be found by viewing the AWS console for the cluster.  Navigate to
https://user-guide.cloud-platform.service.justice.gov.uk/documentation/getting-started/accessing-the-cloud-console.html
and login to the console.
Access to the raw OpenSearch indexes is only possible from the Cloud Platform `hmpps-prisoner-search` family of
namespaces.  From there, select the
[Amazon OpenSearch Service](https://eu-west-2.console.aws.amazon.com/aos/home?region=eu-west-2#opensearch/dashboard).
Viewing the `opensearch` secret in the namespace will provide the name of the relevant OpenSearch instance.

## Connecting to OpenSearch
For OpenSearch it is a case of port forwarding through the OpenSearch proxy pod to connect:
```shell
NAMESPACE=hmpps-prisoner-search-<<env>> && kubectl -n $NAMESPACE port-forward \
  $(kubectl -n $NAMESPACE get pods | awk '/opensearch-proxy-cloud-platform.*Running/ { print $1;exit}') 19200:8080
```
will then start a port forward on local port `19200`.

## Useful commands
The following `http` command in any environment would return a list all indexes e.g.:

```shell
http http://localhost:19200/_cat/indices\?v
```
gives:
```
health status index                          uuid                   pri rep docs.count docs.deleted store.size pri.store.size
green  open   prisoner-search                YkkARHiGQUCs5YxmSdHuKQ   5   1    8809747      1313777      4.1gb            2gb
green  open   .plugins-ml-config             svijQTwKSlWlXCDoJzLWdA   5   1          1            0      9.4kb          4.7kb
green  open   .opensearch-observability      oIoQwOloSiuaiXtEsTQVoA   1   1          0            0       416b           208b
green  open   .plugins-ml-jobs               MMRPxYy3Ttq5yLPlSV2JmA   1   1          1            0     12.6kb          6.3kb
green  open   .kibana_5                      D1fYHodiQLSHUD1LcwznHw   1   1          5            0     21.8kb         10.9kb
green  open   .opendistro-job-scheduler-lock W1KwAIhtTIivmIshC1nRPw   1   1          1           19    161.9kb         88.5kb
green  open   .kibana_2                      dacG4OAfSmWfKXSh1uLywg   1   1          2            0     20.3kb         10.1kb
green  open   .kibana_1                      AJVWinlGTj6lo2WNbF7q1g   1   1          1            0     10.3kb          5.1kb
green  open   .kibana_4                      aEPeGBDpTdCz6D3khj4rVg   1   1          4            0     21.1kb         10.5kb
green  open   .kibana_3                      _HDhNftGT22jalMaAzttkQ   1   1          3            0     20.7kb         10.3kb
green  open   prisoner-index-status          EUEkGVQYRGWFcZb9JNL5Jg   1   1          1            0      9.3kb          4.6kb
```

The full API references is available at https://opensearch.org/docs/2.11/api-reference/index/ - version might need to
be changed to the current one though.

## Retrieving status information
The `prisoner-index-status` index contains just one document with the current state.
```shell
http http://localhost:19200/prisoner-index-status/_doc/STATUS
```
will display the information.  Note that this document is also exposed by visiting
https://prisoner-search-indexer.prison.service.justice.gov.uk/info directly too.

## Cluster health

`http 'http://localhost:19200/_cluster/health'`

The cluster health status is: green, yellow or red. On the shard level, a red status indicates that the specific shard is not allocated in the cluster, yellow means that the primary shard is allocated but replicas are not, and green means that all shards are allocated. The index level status is controlled by the worst shard status. The cluster status is controlled by the worst index status.

## Shards
`http 'http://localhost:19200/_cat/shards'`

The shards command is the detailed view of what nodes contain which shards. It will tell you if it’s a primary or replica, the number of docs, the bytes it takes on disk, and the node where it’s located.
