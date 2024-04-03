# Cosmos Candyfloss for Real-time Json Kafka Streams processing

The project is designed to modular with 2 main modules:

1. **json-transformation**: accepts a json input and uses json path/jolt to transform it to a list of json objects.
2. **candyfloss**: the Kafka streaming app, that constructs a pipeline from the first two components to transform the
   messages and output them to Kafka topic.

## Requirements

1. Java 17 or higher.

## Running the tests

`./gradlew test`

## Deploying to Kubernetes

### Building a Docker image
1. Use base image `docker/base/Dockerfile` as base image. Once uploaded to a docker repo, updated `jib.from.image`
in `candyflow/build.gradle` to point to it.
2. Change `jib.to.image` to upload the docker image.
3. run `./gradlew :candyflow:job` to upload the image.

### Configuring a Kubernetes stateful deployment
TBD

## Adding new transformation

1. Checkout the latest version from git
2. Create a development branch: `git checkout -b my-new-fancy-transformation`
3. Add the new model to `candyfloss/src/main/resources/application.dev.json` and (
   optionally) `candyfloss/src/main/resources/application.prod.json`. *Note* the "NAME" chosen is important to be
   consistent
   for the key in the application config, the name of the pipeline file and the name of the test directory that contains
   the test files related to the model.
    ```jsonc
   kstream {
    ..
    pipeline = {
      ...//other previous yang models
      NAME {
        output.topic.name = KAFKA TOPIC NAME
        file = pipeline/NAME.json
      }
    }
   }
    ```
4. Add your transformation code to `candyfloss/src/main/resources/pipeline/NAME.json`
    ```jsonc
    // Your new transformation
    {
      // Only messages that matches the following condition will be transformed
      "match": {
          "jsonpath": "$.telemetry_data.encoding_path", // JSON path, see for full spec https://github.com/json-path/JsonPath 
          "value": "openconfig-interfaces:interfaces" // The value expected at the selected JSON Path
      },
      "transform": [your fancy jolt transformation],
      // Counter normalization to operate on the transfomed messages, leave it empty '{}' if you don't want to use any counter normalization 
      "normalizeCounters": {
          // Extract time value to compare the messages
          "timestamp-extractor": {
              "jsonpath": "$.msg_timestamp",
              "timestamp-type": "EpochMilli" // Supported values "RFC2822", "EpochMilli", "EpochSeconds"
          },
          // List extracted counters to be normalized
          "counters": [
              "match": {},
              // Key is used to uniquely identify the counter in the state store
              // we list an example here, but the key is very dependent on the specific YANG model
              "key": [
                  {
                    "jsonpath": "$.node_id" // Exract node_id from the transfomed message
                  },
                  {
                    "jsonpath": "$.name" // Exract interface name from the transfomed message
                  },
                  {
                    "jsonpath": "$.index" // Exract interface index from the transfomed message
                  },
                  {
                    "constant": "in_broadcast_pkts" // Assign constant value to the counter key, since a single message will typically hold multiple counters
                  }
              ],
              "type": "u64", // Use to figure out if the counter has reached it's max value and started again from zero
              // The value field of the counter to be extracted from the transfomed message
              "value": {
                  "jsonpath": "$.in_discards"
              }
          ]
      }
    }
    ```
5. Adding test
    1. create new folder for the new YANG model in `candyfloss/src/test/resources/deployment/NAME`. It's important to
       use
       the same `NAME` used in the application config, we auto-discover the relevant tests for each model using the
       name.
    2. Create a folder with a descriptive name for each test case
       in `candyfloss/src/test/resources/deployment/NAME/01-juniper-interface`. *Note that we keep counter state for
       each
       test
       case.*
    3. For each test case add the following files:
        1. `01-input.json`, `02-input.json`, ... Those are the input files from the source Kafka topic. At least one is
           required.
        2. If the message is expected to be transformed correctly, add corresponding `01-output.json`, `02-output.json`
           ...
            1. _Note 1_ : output json is a list `[]` since one input message can be transformed to multiple ones. Think
               for example each message for every sub-interface.
            2. _Note 2_ : output json is an empty `[]` if for whatever reason we skip the message in the transformation.
               For example we don't want to ingest it in Druid.
        3. `01-discard.json`, `02-discard.json`, ... if the message is discarded by the first match
           in  `pipeline.dev.json` or `pipeline.prod.json`
        4. `01-dlq.json`, `02-dlq.json`, .. if the message produces an error message that goes to the dead-letter queue
           Kafka topic.
6. (Optional) run test locally `./gradlew test`
7. Push the changes to bitbuck branch, for example `git push -u my-new-fancy-transformation`
8. Monitor the results of Jenkins build, a build status message will appear next to your branch on bitbucket that will
   take you directly to the Jenkins build.
9. Create a pull request in bitbucket to merge your changes, once approved we can merge to main.

## Counter normalization algorithm

### Preprocessing step. The goal to have valid counters as BigInteger.

1. Extract from the message counterKey/counterValue based on the provided configurations.
2. CounterValue is not a number:
    1. report the message to DLQ
    2. Drop the message! so we don't want to keep propagating invalid values upstream to Druid
3. Check the counter user-provided config is either u32 or u64 -> report error at application start

### Comparing state

1. If the counter didn't exist in the key/value store (`kvStore`) or older than a preconfigured value:
    1. Store the counter in the `kvStore.put(counterKey, counterValue, msg.timestamp)`.
    2. Set the counter value in the message to zero.
    3. Log a new counter event.
2. If the counter value was in the key/value store.
    1. if the value is larger than the cached one: set the value in the message to `counterValue - kv.get(counterKey)`
    2. Else, we need to guess is this a counter round around or a reset
        1. compute the reminder to either Max Unsigned Integer or Max Unsigned Long (based on the user-provided config).
        2. compute `diff = reminder + counterValue`
        3. `if diff < threshold && timeDiff <= counterWrapAroundTimeMs` then is a normal counter wrapping the normalized
           value will be the same as `diff`.
        4. `else` this is a reset, the normalized value will be zero.
