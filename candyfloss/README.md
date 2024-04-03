## Adding new YANG models

The configuration for the YANG Model is added a step in `src/main/resources/pipeline.dev.json`
and `src/main/resources/pipeline.prod.json`.

## Testing YANG Models

For each model need to add a folder to `src/test/resources/deployment` with following properties

- Folder follow the pattern `{NUMBER}-{YANG model name}`; e.g. `00-oc-interface`. The number is the index of the
  configuration step
  in `src/main/resources/pipeline.dev.json`
  and `src/main/resources/pipeline.prod.json`. The second part after the number is not
  important, but ideally should be indicative of the yang model name for readability.
- The folder contains multiple test cases for the given model; e.g. `00-oc-interface/single-interface`
  , `00-oc-interface/subinterfaces`. Each test case is in its own directory.
- Each test case, can contain multiple input/output/dlq/discard files. These files are as follows
    - `{NUMBER}-input.json` the raw data coming from the Kafka topic, e.g., `01-input.json`
    - `{NUMBER}-output.json` the expected output messages for the input of the same number. Note, this is list, since a
      single input message might output multiple messages to Kafka.
    - `{NUMBER}-dlq.json` the expected message to the DLQ topic (dead-letter-queue) for the input of the same number.
      Note, this is list, since a single input message might output multiple messages to Kafka.
    - `{NUMBER}-discard.json` the expected message to the discard topic for the input of the same number. Note, this is
      list, since a single input message might output multiple messages to Kafka.
- We reset the application state for each test case (e.g., reset counter normalization cache). To test counter
  normalization, just add multiple inputs in a single test case. For example `01-input.json`, `02-input.json` and
  then `01-output.json`, `02-output.json` to test the counters have been normalized correctly.
