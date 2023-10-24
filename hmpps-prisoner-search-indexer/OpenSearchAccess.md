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
health status index                     uuid                   pri rep docs.count docs.deleted store.size pri.store.size
green  open   .plugins-ml-config        i-QY7oI-Qey0RBoyHGYK4A   5   1          1            0      9.6kb          4.8kb
green  open   .opensearch-observability Ngmv0-CFQR-IvXAKr_qrog   1   1          0            0       460b           230b
green  open   restore-status            b0CYTCpMSnGu8ON2Z_2SrQ   5   1          1            0      8.3kb          4.1kb
green  open   prisoner-search-blue      3mpojzxLRNyVgUKSJ7oIeA   5   1    4888409      1008293      2.1gb            1gb
green  open   prisoner-search-green     MfvaqJLERTCBlrziTv3gMA   5   1    4888409       155072      1.8gb        959.4mb
green  open   .kibana_2                 z1bL7hkhS_Cn-zyHqsH98w   1   1          2            0     20.3kb         10.1kb
green  open   .kibana_1                 njK7fVWxQ7-7KEH5Yqz-JQ   1   1          1            0     10.3kb          5.1kb
green  open   prisoner-index-status     ne_7sGyXQBmp95946r0PEA   1   1          1            0     11.9kb          5.9kb
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
