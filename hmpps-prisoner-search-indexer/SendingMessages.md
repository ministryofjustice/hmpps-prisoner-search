# Sending messages to the index queue
This application uses a service account called `hmpps-prisoner-search-indexer` with privileges to send messages.
This means that access keys and secrets are not required to send messages as the service account is bound to the
deployment.

To therefore send a test message requires spinning up a pod with those privileges.  The following command will start a
pod called `debug` and start a shell in the pod:
```shell
kubectl run -it --rm debug --image=ghcr.io/ministryofjustice/hmpps-devops-tools:latest --restart=Never \
  --overrides='{ "spec": { "serviceAccount": "hmpps-prisoner-search-indexer" }  }' -- bash
```

A test message in the pod can then be sent by running commands similar to these:
```shell
aws --queue-url=https://sqs.eu-west-2.amazonaws.com/754256621582/syscon-devs-dev-hmpps_prisoner_search_index_queue \
  sqs send-message --message-body '{"type":"POPULATE_PRISONER_PAGE","prisonerPage":{"page":1000,"pageSize":10}}'
```

```shell
aws --queue-url=http://sqs.eu-west-2.localhost.localstack.cloud:4566/000000000000/offender-queue \
sqs send-message --message-body '{ "MessageId": "123456", "Message": "{\"eventType\": \"OFFENDER-UPDATED\", \"offenderIdDisplay\": \"G6586VW\"}", "MessageAttributes": {"eventType" : { "Value":"OFFENDER-UPDATED"}}}' \
--message-attributes '{"eventType": { "DataType":"String", "StringValue":"OFFENDER-UPDATED"}}'
```

```shell
aws --queue-url=http://sqs.eu-west-2.localhost.localstack.cloud:4566/000000000000/hmpps-domain-queue \
  sqs send-message --message-body '{ "MessageId": "123456", "Message": "{\"eventType\": \"incentives.iep-review.updated\", \"additionalInformation\": {\"nomsNumber\": \"A6923DZ\", \"id\": 12345678}, \"description\": \"test message 1\"}", "MessageAttributes": {"eventType" : { "Value":"incentives.iep-review.updated"}}}' \
 --message-attributes '{"eventType": { "DataType":"String", "StringValue":"incentives.iep-review.updated"}}'
```

If running the commands in a namespace other than dev then the `queue-url` can be obtained from the `sqs_queue_url`
value in the `prisoner-search-indexer-queue` secret.

The document in the index can be inspected by a command like this:

```shell
http http://os01.eu-west-2.opensearch.localhost.localstack.cloud:4566/prisoner-search/_doc/A6923DZ
```
