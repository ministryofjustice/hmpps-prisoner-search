# HMPPS Prisoner Search Indexer

[![repo standards badge](https://img.shields.io/badge/dynamic/json?color=blue&style=flat&logo=github&label=MoJ%20Compliant&query=%24.result&url=https%3A%2F%2Foperations-engineering-reports.cloud-platform.service.justice.gov.uk%2Fapi%2Fv1%2Fcompliant_public_repositories%2Fhmpps-prisoner-search-indexer)](https://operations-engineering-reports.cloud-platform.service.justice.gov.uk/public-github-repositories.html#hmpps-prisoner-search-indexer "Link to report")
[![CircleCI](https://circleci.com/gh/ministryofjustice/hmpps-prisoner-search-indexer/tree/main.svg?style=svg)](https://circleci.com/gh/ministryofjustice/hmpps-prisoner-search-indexer)
[![Docker Repository on Quay](https://quay.io/repository/hmpps/hmpps-prisoner-search-indexer/status "Docker Repository on Quay")](https://quay.io/repository/hmpps/hmpps-prisoner-search-indexer)
[![API docs](https://img.shields.io/badge/API_docs_-view-85EA2D.svg?logo=swagger)](https://hmpps-prisoner-search-indexer-dev.hmpps.service.justice.gov.uk/webjars/swagger-ui/index.html?configUrl=/v3/api-docs)

The purpose of this service is to:
* Keep the OpenSearch (OS) prison index up to date with changes from Prison systems (NOMIS)
* Rebuild the index when required without an outage

