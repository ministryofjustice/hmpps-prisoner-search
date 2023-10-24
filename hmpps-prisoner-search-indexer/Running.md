# Running Prisoner Search Indexer

`localstack` is used to emulate the AWS SQS and OpenSearch service.

#### Running prisoner search indexer in Docker

*WE STRONGLY ADVISE, WHEN RUNNING LOCALLY, TO POINT AT SERVICES IN THE T3/DEV ENVIRONMENT*

Since the indexer has a number of dependencies that themselves have dependencies, it is recommended that when running
locally you point mostly at services running in T3. The exception is pointing at localstack for AWS services,
but HMPPS Auth, Prison Api, incentives services, restricted patients are best used directly on T3 dev environment.
The docker-compose.yml file is provided as reference but the dependencies of dependencies are not shown and are subject to change.
To run against T3 dependencies the environment variables as shown in the helm_deploy/values-dev.yaml file for paths can be used.
Personal client credentials for the T3 services can be obtained from the HMPPS Auth team with the appropriate roles.

To start up localstack and other dependencies with prisoner indexer running in Docker too:
```shell
docker compose up localstack hmpps-auth prison-api restricted-patients hmpps-incentives-api hpsi-db
```

Once localstack has started then, in another terminal, run the following command to start prisoner indexer too:
```shell
docker compose up hmpps-prisoner-search-indexer --detach
```
  To check that it has all started correctly use `docker ps`.

#### Punning prisoner search indexer in IntelliJ or on the command line
To start up localstack and other dependencies with prisoner offender search running in IntelliJ:
```shell
docker compose up --scale hmpps-prisoner-search-indexer=0
```
To then run prisoner offender search from the command line:
```
SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun
```
Alternatively create a Spring Boot run configuration with active profile of `dev` and main class
`uk.gov.justice.digital.hmpps.prisonersearch.search.HmppsPrisonerSearch`.

#### Running the tests
If just running the tests then the following will just start localstack as the other dependencies are mocked out:

```shell
docker compose -f docker-compose-localstack-tests.yml up
```
Then the following command will run all the tests:
```shell
./gradlew test
```

#### Deleting localstack data between runs
Since localstack persists data between runs it may be necessary to delete the localstack temporary data:

Mac
```shell
rm -rf $TMPDIR/data
```
Linux
```shell
sudo rm -rf /tmp/localstack
```
Docker Desktop for Windows (started using `docker-compose-windows.yml`)
```shell
docker volume rm -f prisoner-offender-search_localstack-vol
```

*Please note the above will not work on a Mac using Docker Desktop since the Docker network host mode is not supported on a Mac*

On Mac it is recommended to run all components *except* prisoner-offender-search (see below). Then run prisoner-offender-search outside of docker using gradle:

```shell
SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun
```

### When running locally you can add some prisoners into Elastic with the following:-

#### Get a token
```shell
TOKEN=$(curl --location --request POST "http://localhost:8090/auth/oauth/token?grant_type=client_credentials" --header "Authorization: Basic $(echo -n prisoner-offender-search-client:clientsecret | base64)" |  jq -r .access_token)
```

#### Start indexing
```shell
curl --location --request PUT "http://localhost:8080/prisoner-index/build-index" --header "Authorization: Bearer $TOKEN" | jq -r
```

#### Check all indexed with
```shell
curl --location --request GET "http://localhost:8080/info" | jq -r
```

#### If 53 records then mark complete
```shell
curl --location --request PUT "http://localhost:8080/prisoner-index/mark-complete" --header "Authorization: Bearer $TOKEN" | jq -r
```

#### Now test a search
```shell
curl --location --request POST "http://localhost:8080/prisoner-search/match" --header "Authorization: Bearer $TOKEN" --header 'Content-Type: application/json' \
 --data-raw '{
    "lastName": "Smith"
 }' | jq -r
```

#### View ES indexes
```shell
curl --location --request POST "http://es01.eu-west-2.es.localhost.localstack.cloud:4566/prisoner-search-a/_search" | jq
```

### Alternative running
Or to just run `localstack` which is useful when running against an a non-local test system, you will  need the `spring.profiles.active=localstack` and `sqs.provider=full-localstack` environment variables:

```shell
TMPDIR=/private$TMPDIR docker-compose up localstack
```

In all of the above the application should use the host network to communicate with `localstack` since AWS Client will try to read messages from localhost rather than the `localstack` network.

### Experimenting with messages

There are two handy scripts to add messages to the queue with data that matches either the dev environment or data in the test Docker version of the apps.

Purging a local queue:
```shell
aws --endpoint-url=http://localhost:4566 sqs purge-queue --queue-url http://localhost:4566/queue/prisoner_offender_index_queue
```
