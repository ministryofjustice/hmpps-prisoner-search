# Prisoner Updates

This service subscribes to both domain events and prison offender events topics using separate queues.

When an event is published AWS puts a message onto one of our event queues.
The event queue then processes that message. If it is a prison offender event, the latest prisoner record is retrieved via the Prison API
 and upserted into the prisoner index.
If it is a domain event, the corresponding domain service is called to get the data and is updated in the index.
See [Useful Queries](./UsefulQueries.md) for information on how to view the events in Application Insights.

If the message processing fails then the message is transferred onto the event dead letter queue (DLQ).  Every
10 minutes the 
[hmpps-prisoner-search-indexer-retry-dlqs](./helm_deploy/hmpps-prisoner-search-indexer/templates/retry-dlqs-cronjob.yaml)
cronjob will transfer all the messages from the DLQ
back onto the main queue to be retried. An alert will be generated if messages stay on the DLQ too long.
