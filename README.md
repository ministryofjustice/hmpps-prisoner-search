# HMPPS Prisoner Search
[![repo standards badge](https://img.shields.io/badge/endpoint.svg?&style=flat&logo=github&url=https%3A%2F%2Foperations-engineering-reports.cloud-platform.service.justice.gov.uk%2Fapi%2Fv1%2Fcompliant_public_repositories%2Fhmpps-prisoner-search)](https://operations-engineering-reports.cloud-platform.service.justice.gov.uk/public-report/hmpps-prisoner-search "Link to report")
[![CircleCI](https://circleci.com/gh/ministryofjustice/hmpps-prisoner-search/tree/main.svg?style=svg)](https://circleci.com/gh/ministryofjustice/hmpps-prisoner-search)
[![Docker Repository on Quay](https://img.shields.io/badge/quay.io-repository-2496ED.svg?logo=docker)](https://quay.io/repository/hmpps/hmpps-prisoner-search)
[![API docs](https://img.shields.io/badge/API_docs_-view-85EA2D.svg?logo=swagger)](https://prisoner-search-dev.prison.service.justice.gov.uk/swagger-ui/index.html)

This README provides information on the prisoner search service.  For the indexer service that keeps the search
up-to-date from Prison systems (NOMIS) please see the [indexer README](hmpps-prisoner-search-indexer/README.md).

The purpose of this service is to:
* API to provides searching of prisoner records in NOMIS via OpenSearch

### Running

`localstack` is used to emulate the AWS OpenSearch service.

#### Running prisoner search in Docker

*WE STRONGLY ADVISE, WHEN RUNNING LOCALLY, TO POINT AT SERVICES IN THE T3/DEV ENVIRONMENT*

Since search has a number of dependencies that themselves have dependencies, it is recommended that when running locally you point mostly at services
running in T3. The exception is pointing at localstack for AWS services, but HMPPS Auth, prison-api, and domain services are best used directly on T3 dev environment.
The docker-compose.yml file is provided as reference but the dependencies of dependencies are not shown and are subject to change.
To run against T3 dependencies the environment variables as shown in the helm_deploy/values-dev.yaml file for paths can be used.
Personal client credentials for the T3 services can be obtained from the HMPPS Auth team with the appropriate roles.

To start up localstack and other dependencies with prisoner search running in Docker too:
```shell
docker compose up localstack hmpps-auth prison-api restricted-patients hmpps-incentives-api ??? hpsi-db
```

Once localstack has started then, in another terminal, run the following command to start prisoner search too:
```shell
docker-compose up hmpps-prisoner-search --detach
```
  To check that it has all started correctly use `docker ps`.

#### Running prisoner search in IntelliJ or on the command line
To start up localstack and other dependencies with prisoner search running in IntelliJ:
```shell
docker compose up --scale hmpps-prisoner-search=0 --scale hmpps-prisoner-search-indexer=0
```
To then run prisoner search from the command line:
```
SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun
```
Alternatively create a Spring Boot run configuration with active profile of `dev` and main class `uk.gov.justice.digital.hmpps.prisonersearch.search.HmppsPrisonerSearch`.

#### Running the tests
If just running the tests then the following will just start localstack as the other dependencies are mocked out:

```shell
docker compose -f docker-compose-test.yml up
```
Then the following command will run all the tests:
```shell
./gradlew test
```

### Raw OpenSearch access

Access to the raw OpenSearch indexes is only possible from the Cloud Platform `hmpps-prisoner-search` family of namespaces.

First step is to setup a port-forward on local port 19200:
```bash
NAMESPACE=hmpps-prisoner-search-dev && kubectl -n $NAMESPACE port-forward $(kubectl -n $NAMESPACE get pods | grep opensearch-proxy-cloud-platform | grep Running | head -1 | awk '{print $1}') 19200:8080
```

Then the following `http` command will return a list all indexes e.g.:

```
http http://localhost:19200/_cat/indices

green open .opensearch-observability 5hpaFEsZQFCHKbK3MUs5ww 1 2       0      0    624b    208b
green open .plugins-ml-config        PLZ29c0fTm-eZE3jbBDZ1g 5 1       1      0   9.5kb   4.7kb
green open new-indexes               awlFmRltTUaODin8FAcV3g 5 1       0      0     2kb     1kb
green open prisoner-search-blue      BJVwzaavQ1S8ZUBjh8m6AA 5 1 4884726 819333     2gb     1gb
green open prisoner-search-green     ah_86gBrSNyuq_ZeOqADWQ 5 1   57188   6449 191.9mb 103.8mb
green open .kibana_2                 pYe_1Di0RCyhfV-P0wbaIA 1 2       2      0  30.5kb  10.1kb
green open .kibana_1                 VsvTD5S8Tv-u7Gu5Hn6Bpg 1 2       1      0  15.5kb   5.1kb
green open prisoner-index-status     pHxkOKu9SgOKAxmZh0hJHQ 1 1       1      0  11.9kb   5.9kb
```
