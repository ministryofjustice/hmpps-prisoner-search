# HMPPS Prisoner Search Indexer

[![repo standards badge](https://img.shields.io/badge/dynamic/json?color=blue&style=flat&logo=github&label=MoJ%20Compliant&query=%24.result&url=https%3A%2F%2Foperations-engineering-reports.cloud-platform.service.justice.gov.uk%2Fapi%2Fv1%2Fcompliant_public_repositories%2Fhmpps-prisoner-search-indexer)](https://operations-engineering-reports.cloud-platform.service.justice.gov.uk/public-github-repositories.html#hmpps-prisoner-search-indexer "Link to report")
[![CircleCI](https://circleci.com/gh/ministryofjustice/hmpps-prisoner-search-indexer/tree/main.svg?style=svg)](https://circleci.com/gh/ministryofjustice/hmpps-prisoner-search-indexer)
[![Docker Repository on Quay](https://quay.io/repository/hmpps/hmpps-prisoner-search-indexer/status "Docker Repository on Quay")](https://quay.io/repository/hmpps/hmpps-prisoner-search-indexer)
[![API docs](https://img.shields.io/badge/API_docs_-view-85EA2D.svg?logo=swagger)](https://prisoner-search-indexer-dev.prison.service.justice.gov.uk/swagger-ui/index.html?configUrl=/v3/api-docs)

The purpose of this service is to:
* Keep the OpenSearch prison index up to date with changes from Prison systems (NOMIS)
* Rebuild the index when required without an outage

## Sending messages to the index queue ##
This application uses a service account called `hmpps-prisoner-search-indexer` with privileges to send messages.
This means that access keys and secrets are not required to send messages as the service account is bound to the 
deployment.

To therefore send a test message requires spinning up a pod with those privileges.  The following command will start a
pod called `debug` and start a shell in the pod:
```bash
kubectl run -it --rm debug --image=ghcr.io/ministryofjustice/hmpps-devops-tools:latest --restart=Never --overrides='{ "spec": { "serviceAccount": "hmpps-prisoner-search-indexer" }  }' -- bash
```

A test message in the pod can then be sent by running commands similar to
```bash
aws --queue-url=https://sqs.eu-west-2.amazonaws.com/754256621582/syscon-devs-dev-hmpps_prisoner_search_index_queue sqs send-message --message-body '{"type":"POPULATE_PRISONER_PAGE","prisonerPage":{"page":1000,"pageSize":10}}'
```
If running the commands in a namespace other than dev then the `queue-url` can be obtained from the `prisoner-search-indexer-queue` secret.
