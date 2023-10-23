# Rebuilding an index

To rebuild an index the credentials used must have the ROLE `PRISONER_INDEX` therefore it is recommend to use client credentials with the `ROLE_PRISONER_INDEX` added and pass in your username when getting a token.
In the test and local dev environments the `prisoner-offender-search-client` has conveniently been given the `ROLE_PRISONER_INDEX`.

The rebuilding of the index can be sped up by increasing the number of pods handling the reindex e.g.:

```
kubectl -n prisoner-offender-search-dev scale --replicas=8 deployment/prisoner-offender-search
```
After obtaining a token for the environment invoke the reindex with a curl command or Postman e.g.:

```
curl --location --request PUT 'https://prisoner-offender-search-dev.hmpps.service.justice.gov.uk/prisoner-index/build-index' \
--header 'Content-Type: application/json' \
--header 'Authorization: Bearer <some token>'
```

For production environments where access is blocked by inclusion lists this will need to be done from within a Cloud Platform pod.

Next monitor the progress of the rebuilding via the info endpoint (e.g. https://prisoner-offender-search-dev.hmpps.service.justice.gov.uk/info). This will return details like the following:

```
    "index-status": {
    "id": "STATUS",
    "currentIndex": "INDEX_A",
    "startIndexTime": "2020-09-23T10:08:33",
    "inProgress": true
    },
    "index-size": {
    "INDEX_A": 702344,
    "INDEX_B": 2330
    },
    "index-queue-backlog": "700000"
```

When `"index-queue-backlog": "0"` has reached zero then all indexing messages have been processed. Check the dead letter queue is empty via the health check (e.g https://prisoner-offender-search-dev.hmpps.service.justice.gov.uk/health). This should show the queues DLQ count at zero, e.g.:
```
    "indexQueueHealth": {
      "status": "UP",
      "details": {
        "MessagesOnQueue": 0,
        "MessagesInFlight": 0,
        "dlqStatus": "UP",
        "MessagesOnDLQ": 0
      }
    },
```

The indexing is ready to marked as complete using another call to the service e.g:

```
curl --location --request PUT 'https://prisoner-offender-search-dev.hmpps.service.justice.gov.uk/prisoner-index/mark-complete' \
--header 'Content-Type: application/json' \
--header 'Authorization: Bearer <some token>'
```

One last check of the info endpoint should confirm the new state, e.g.:

```
    "index-status": {
    "id": "STATUS",
    "currentIndex": "INDEX_B",
    "startIndexTime": "2020-09-23T10:08:33",
    "endIndexTime": "2020-09-25T11:27:22",
    "inProgress": false
    },
    "index-size": {
    "INDEX_A": 702344,
    "INDEX_B": 702344
    },
    "index-queue-backlog": "0"
```

Pay careful attention to `"currentIndex": "INDEX_A"` - this shows the actual index being used by clients.
