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

A test message in the pod can then be sent by running commands similar to
```shell
aws --queue-url=https://sqs.eu-west-2.amazonaws.com/754256621582/syscon-devs-dev-hmpps_prisoner_search_index_queue
  \ sqs send-message --message-body '{"type":"POPULATE_PRISONER_PAGE","prisonerPage":{"page":1000,"pageSize":10}}'
```
If running the commands in a namespace other than dev then the `queue-url` can be obtained from the `sqs_queue_url`
value in the `prisoner-search-indexer-queue` secret.
