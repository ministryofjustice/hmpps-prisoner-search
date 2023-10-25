# Regression Tests

Recommended regression tests is as follows:

A partial build of index - see Index rebuilds section of [Index Maintenance](./IndexMaintenance.md). 
The rebuild does not need to be completed but expect the info to show something like this:
```json
"index-status": {
  "currentIndex": "GREEN",
  "currentIndexStartBuildTime": "2020-09-23T10:08:33",
  "currentIndexState": "BUILDING",
},
"index-size": {
  "INDEX_A": 579543,
  "INDEX_B": 521
},
"index-queue-backlog": "578975"
```
So long as the index is being populated and the `index-queue-backlog` figure is decreasing after some time 
(e.g. 10 minutes) it demonstrates the application is working.

Check the health endpoint to show the Index dead letter queue is not building up with errors (e.g. `/health`):
```json
"indexQueueHealth": {
  "details": {
    "messagesOnQueue": 41834,
    "messagesInFlight": 4,
    "dlqStatus": "UP",
    "messagesOnDlq": 0
  },
  "status": "UP"
}
```
The above result indicates a valid state since the `messagesOnDlq` would be zero.

The build can either be left to run or cancelled using the `/maintain-index/cancel` endpoint.
