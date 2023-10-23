# Prisoner Updates

This service subscribes to both domain events and prison offender events.

When an event is received a message is put onto the event queue.  The event queue then processes that message -
the latest prisoner record is retrieved via the Prison API, augmented with incentives and restricted patient data
and upserted into the prisoner index.
If the message processing fails then the message is transferred onto the event dead letter queue (DLQ).  Every
10 minutes the event will be retried and an alert then generated if messages stay on the queue too long.

