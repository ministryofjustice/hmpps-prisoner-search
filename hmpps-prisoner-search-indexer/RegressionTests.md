# Regression Tests

Recommended regression tests is as follows:

* A partial build of index - see the `Rebuilding an index` instructions below. The rebuild does not need to be completed but expect the info to show something like this:
```
    "index-status": {
    "id": "STATUS",
    "currentIndex": "INDEX_A",
    "startIndexTime": "2020-09-23T10:08:33",
    "inProgress": true
    },
    "index-size": {
    "INDEX_A": 579543,
    "INDEX_B": 521
    },
    "index-queue-backlog": "578975"
```
So long as the index is being populated and the ` "index-queue-backlog"` figure is decreasing after some time (e.g. 10 minutes) it demonstrates the application is working.

Check the health endpoint to show the Index DLQ is not building up with errors (e.g. `https://prisoner-search-dev.hmpps.service.justice.gov.uk/health`):
```
    "indexQueueHealth": {
      "status": "UP",
      "details": {
        "MessagesOnQueue": 41834,
        "MessagesInFlight": 4,
        "dlqStatus": "UP",
        "MessagesOnDLQ": 0
      }
    }
```
The above result indicates a valid state since the `MessagesOnDLQ` would be zero.

The build can either be left to run or cancelled using the following endpoint:
 ```
curl --location --request PUT 'https://prisoner-search-dev.hmpps.service.justice.gov.uk/prisoner-index/cancel-index' \
--header 'Content-Type: application/json' \
--header 'Authorization: Bearer <some token>'
 ```
