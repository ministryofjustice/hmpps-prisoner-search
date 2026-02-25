# Generating the infrastructure client

The generated APIs rely on a generated org.openapitools.client.infrastructure.ApiClient. Since we have lots of generated
APIs we only want one version of the client, so we have copied the client to `src/main/kotlin`. Also, by default, the
client is generated with `protected` functions, but we need these to be public in order to call the methods.  Running:
```shell
./generate-infrastructure-client.bash
```
will generate the client and copy it into `src/main/kotlin`, removing `protected` from the functions.

This will be necessary if the openapi generator is updated and the API clients that are generated no longer compile.
