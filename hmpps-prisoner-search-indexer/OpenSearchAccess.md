# Raw OpenSearch access

Access to the raw OpenSearch indexes is only possible from the Cloud Platform `prisoner-offender-search` family of namespaces.

For instance, the following curl command in any environment would return a list all indexes e.g.:

```
http http://aws-es-proxy-service:9200/_cat/indices

green open prisoner-search-a     tlGst8dmS2aE8knxfxJsfQ 5 1 2545309 1144511   1.1gb 578.6mb
green open offender-index-status v9traPPRS9uo7Ui0J6ixOQ 1 1       1       0  10.7kb   5.3kb
green open prisoner-search-b     OMcdEir_TgmTP-tzybwp7Q 5 1 2545309  264356 897.6mb 448.7mb
green open .kibana_2             _rVcHdsYQAKyPiInmenflg 1 1      43       1 144.1kb    72kb
green open .kibana_1             f-CWilxMRyyihpBWBON1yw 1 1      39       6 176.3kb  88.1kb
```
