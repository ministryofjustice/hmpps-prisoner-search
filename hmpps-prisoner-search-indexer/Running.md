# Running Prisoner Search Indexer

`localstack` is used to emulate the AWS SQS, SNS and OpenSearch service.

*WE STRONGLY ADVISE, WHEN RUNNING LOCALLY, TO POINT AT SERVICES IN THE T3/DEV ENVIRONMENT*

Since the indexer has a number of dependencies that themselves have dependencies, it is recommended that when running
locally you point mostly at services running in T3. The exception is pointing at localstack for AWS services,
but HMPPS Auth, Prison Api, incentives services, restricted patients are best used directly on T3 dev environment.
The docker-compose.yml file is provided as reference but the dependencies of dependencies are not shown and are subject
to change. To run against T3 dependencies the environment variables as shown in the helm_deploy/values-dev.yaml file
for paths can be used, and are present commented out in application-dev.yml. Personal client credentials for the T3 services can be obtained from the HMPPS Auth team 
with the appropriate roles.

To set up the index, run one of the search tests before running the indexer, e.g. from the uk.gov.justice.digital.hmpps.prisonersearch.search.resource package.

## Running prisoner search indexer in IntelliJ or on the command line
To run the indexer from the command line:
```shell
SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun
```
Alternatively create a Spring Boot run configuration with active profile of `dev` and main class
`uk.gov.justice.digital.hmpps.prisonersearch.indexer.HmppsPrisonerSearchIndexer`.

## Running the tests
If just running the tests then the following will just start localstack as the other dependencies are mocked out:
```shell
docker compose -f docker-compose-test.yml pull && docker compose -f docker-compose-test.yml up -d
```

Then the following command will run all the tests:
```shell
./gradlew clean test
```

## Localstack OpenSearch access
If you have started localstack using `docker-compose-test.yml` then
```shell
http http://os01.eu-west-2.opensearch.localhost.localstack.cloud:4566/_cat/indices
```
will output the indices.

