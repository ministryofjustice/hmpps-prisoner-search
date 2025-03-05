# HMPPS Prisoner Search Indexer
[![CircleCI](https://circleci.com/gh/ministryofjustice/hmpps-prisoner-search/tree/main.svg?style=svg)](https://circleci.com/gh/ministryofjustice/hmpps-prisoner-search)
[![Docker Repository on Quay](https://img.shields.io/badge/quay.io-repository-2496ED.svg?logo=docker)](https://quay.io/repository/hmpps/hmpps-prisoner-search-indexer)
[![API docs](https://img.shields.io/badge/API_docs_-view-85EA2D.svg?logo=swagger)](https://prisoner-search-indexer-dev.prison.service.justice.gov.uk/swagger-ui/index.html)
[![Event docs](https://img.shields.io/badge/Event_docs-view-85EA2D.svg)](https://studio.asyncapi.com/?url=https://raw.githubusercontent.com/ministryofjustice/hmpps-prisoner-search/main/hmpps-prisoner-search-indexer/async-api.yml&readOnly)

The purpose of this service is to:
* Keep the OpenSearch prison index up to date with changes from Prison systems (NOMIS)
* Send domain events when prisoner data has been updated

## Sections
* [Prisoner updates](./PrisonerUpdates.md)
* Development
  * [Running](./Running.md)
  * [Regression tests](./RegressionTests.md)
* Support
  * [Index maintenance](./IndexMaintenance.md)
  * [Prisoner differences](./PrisonerDifferences.md)
  * [Alerts](./Alerts.md)
  * [Accessing OpenSearch directly](./OpenSearchAccess.md)
  * [Restoring snapshots](./RestoringSnapshots.md)
  * [Sending messages to the queues](./SendingMessages.md)
  * [Useful application insights queries](./UsefulQueries.md)
