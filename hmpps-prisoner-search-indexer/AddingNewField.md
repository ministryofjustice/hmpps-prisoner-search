# Adding a new field to the index

When adding a new field to the index in the Prisoner class, you will need to be careful not to trigger `prisoner-offender-search.prisoner.updated` domain events on every prisoner in the system.
This can happen if the Prisoner.translate() method is updated to include the new field, and the @DiffableProperty annotation is added to the new field.

This is because the refresh cronjob (which currently runs every night in prod) looks for any discrepancies and when it finds one it sends an event as well as updating the index.
If you add a new field to the index, the refresh cronjob will see that every prisoner is missing this field and send an event for every prisoner.

This can be avoided by either:
- running a build in each environment as soon as the new code is deployed, ensuring it will finish before a refresh is started. The build process never sends events. Or (preferably) ...
- initially omitting the @DiffableProperty annotation from the new field, so it is not considered by the PrisonerDifferenceService,
then waiting for the nightly refresh which will silently update it, and then adding the annotation in a subsequent release.
