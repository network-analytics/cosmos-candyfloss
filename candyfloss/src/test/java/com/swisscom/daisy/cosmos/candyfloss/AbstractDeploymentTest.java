package com.swisscom.daisy.cosmos.candyfloss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swisscom.daisy.cosmos.candyfloss.config.JsonKStreamApplicationConfig;
import com.swisscom.daisy.cosmos.candyfloss.config.PipelineStepConfig;
import com.swisscom.daisy.cosmos.candyfloss.config.exceptions.InvalidConfigurations;
import com.swisscom.daisy.cosmos.candyfloss.testutils.JsonUtil;
import com.swisscom.daisy.cosmos.candyfloss.transformations.match.exceptions.InvalidMatchConfiguration;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.*;
import org.json.JSONException;
import org.skyscreamer.jsonassert.JSONAssert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class AbstractDeploymentTest {
  private final Logger logger = LoggerFactory.getLogger(AbstractDeploymentTest.class);
  protected final ObjectMapper objectMapper = new ObjectMapper();
  protected static final String DISCARD_TEST = "discard";
  protected JsonKStreamApplicationConfig appConf;
  protected TestInputTopic<String, String> inputTopic;
  protected Map<String, TestOutputTopic<String, String>> outputTopics;
  protected TestOutputTopic<String, String> discardTopic;
  protected TestOutputTopic<String, String> dlqTopic;

  protected TopologyTestDriver setupTopology(String applicationConfigFileName)
      throws IOException, InvalidConfigurations, InvalidMatchConfiguration {
    Config config = ConfigFactory.load(applicationConfigFileName);
    appConf = JsonKStreamApplicationConfig.fromConfig(config);
    CandyflossKStreamsApplication app = new CandyflossKStreamsApplication(appConf);
    final Topology topology = app.buildTopology();
    var kafkaConfig = appConf.getKafkaProperties();
    kafkaConfig.setProperty("application.id", UUID.randomUUID().toString());
    TopologyTestDriver topologyTestDriver = new TopologyTestDriver(topology, kafkaConfig);
    var stringSerde = Serdes.String();
    inputTopic =
        topologyTestDriver.createInputTopic(
            appConf.getInputTopicName(), stringSerde.serializer(), stringSerde.serializer());
    outputTopics =
        appConf.getPipeline().getSteps().values().stream()
            .collect(
                Collectors.toMap(
                    PipelineStepConfig::getOutputTopic,
                    x ->
                        topologyTestDriver.createOutputTopic(
                            x.getOutputTopic(),
                            stringSerde.deserializer(),
                            stringSerde.deserializer())));
    dlqTopic =
        topologyTestDriver.createOutputTopic(
            appConf.getDlqTopicName(), stringSerde.deserializer(), stringSerde.deserializer());
    discardTopic =
        topologyTestDriver.createOutputTopic(
            appConf.getDiscardTopicName(), stringSerde.deserializer(), stringSerde.deserializer());
    return topologyTestDriver;
  }

  /*** Auxiliary method to check that topic produced the expected output*/
  protected void checkOutput(
      TestOutputTopic<String, String> topic, Optional<List<Map<String, Object>>> expectedOutput)
      throws JSONException, JsonProcessingException {
    List<KeyValue<String, String>> returned =
        topic == null ? List.of() : topic.readKeyValuesToList();
    returned.stream()
        .map(x -> x.value)
        .forEach(y -> logger.info("Produced message on topic {}: {}", topic, y));
    if (expectedOutput.isPresent()) {
      var expected = expectedOutput.get();
      assertEquals(
          expected.size(),
          returned.size(),
          String.format(
              "The number of produces message is not the same as the expected ones for topic: %s",
              topic));
      for (int i = 0; i < expected.size(); i++) {
        JSONAssert.assertEquals(
            objectMapper.writer().writeValueAsString(expected.get(i)), returned.get(i).value, true);
      }
      JSONAssert.assertEquals(
          objectMapper.writer().writeValueAsString(expected),
          String.format("[%s]", String.join(",", returned.stream().map(x -> x.value).toList())),
          true);
    } else {
      String msg =
          String.format("Output topic %s' contains values, while expecting it to be empty", topic);
      assertEquals(0, returned.size(), msg);
    }
  }

  /*** For a given path, extract all files that their name ends with an `ending`, used for automatic test files discovery.*/
  private Map<Integer, Path> extractFiles(Path path, String ending) throws IOException {
    try (var files = Files.list(path).filter(p -> !Files.isDirectory(p))) {
      return files
          .filter(p -> p.toFile().getName().endsWith(ending))
          .collect(
              Collectors.toMap(x -> Integer.parseInt(x.toFile().getName().split("-")[0]), x -> x));
    }
  }

  /*** Read the expected JSON output and convert it to List of JSON objects, for easier compare during testing */
  protected Optional<List<Map<String, Object>>> getExpectedOutput(Optional<Path> resourcePath)
      throws IOException {
    final Optional<InputStream> outputResource =
        resourcePath.map(
            x -> {
              try {
                return new FileInputStream(x.toFile());
              } catch (FileNotFoundException e) {
                fail(
                    String.format(
                        "Couldn't load file resource '%s', raised exception: %s",
                        x, e.getMessage()));
              }
              return null;
            });
    if (outputResource.isPresent()) {
      return Optional.of(JsonUtil.readJsonArray(outputResource.get()));
    } else {
      return Optional.empty();
    }
  }

  /*** Run test on a single input files and expected outputs to the normal output topic, dlq topic and discard */
  protected void testCase(
      TestOutputTopic<String, String> outputTopic,
      Path inputResourcePath,
      Optional<Path> outputResourcePath,
      Optional<Path> dlqResourcePath,
      Optional<Path> discardResourcePath)
      throws IOException, JSONException {
    logger.info(
        "Running test on files: input='{}', output='{}', dlq='{}', discard='{}'",
        inputResourcePath,
        outputResourcePath,
        dlqResourcePath,
        discardResourcePath);
    var input = JsonUtil.readFromInputStream(new FileInputStream(inputResourcePath.toFile()));
    var inputMap = JsonUtil.readJson(new FileInputStream(inputResourcePath.toFile()));
    final Optional<List<Map<String, Object>>> expectedOutput =
        getExpectedOutput(outputResourcePath);
    final Optional<List<Map<String, Object>>> expectedDlq = getExpectedOutput(dlqResourcePath);
    final Optional<List<Map<String, Object>>> expectedDiscard =
        getExpectedOutput(discardResourcePath);
    final String testKey = "testKey";

    var dateTimeString = (String) inputMap.get("timestamp");
    final ZonedDateTime zoned;
    if (dateTimeString != null) {
      DateTimeFormatter formatter;
      if (dateTimeString.contains("-")) {
        formatter =
            new DateTimeFormatterBuilder()
                .append(DateTimeFormatter.ISO_LOCAL_DATE)
                .appendLiteral(' ')
                .append(DateTimeFormatter.ISO_LOCAL_TIME)
                .toFormatter();
        var time = LocalDateTime.parse(dateTimeString, formatter);
        zoned = time.atZone(ZoneId.of(ZoneId.SHORT_IDS.get("ECT")));
      } else {
        var split = dateTimeString.split("\\.");
        var instance =
            Instant.ofEpochSecond(
                Long.parseUnsignedLong(split[0]), Long.parseUnsignedLong(split[1]));
        zoned = instance.atZone(ZoneId.of(ZoneId.SHORT_IDS.get("ECT")));
      }
    } else {
      zoned = ZonedDateTime.now();
    }

    inputTopic.pipeInput(testKey, input, zoned.toInstant());

    checkOutput(outputTopic, expectedOutput);
    checkOtherOutput(inputResourcePath, outputTopic);
    checkOutput(discardTopic, expectedDiscard);
    checkOutput(dlqTopic, expectedDlq);
  }

  private void checkOtherOutput(
      Path inputPath, TestOutputTopic<String, String> currentOutputTopic) {
    outputTopics.values().stream()
        .filter(t -> t != currentOutputTopic)
        .toList()
        .forEach(
            topic -> {
              var recordList = topic.readRecordsToList();
              if (!recordList.isEmpty()) {
                logger.warn(
                    "Other config's topic "
                        + topic
                        + " also has output from input file: "
                        + inputPath.subpath(inputPath.getNameCount() - 3, inputPath.getNameCount())
                        + ". Please check if it's expected.");
              }
            });
  }

  /*** Run the tests in testFixturesPath (aka a single folder in the test folder). */
  protected void testImpl(String applicationConfigFileName, String env, String name, Path testCase)
      throws InvalidConfigurations, IOException, JSONException, InvalidMatchConfiguration {
    logger.info("Running tests in: {}", testCase);
    final var topologyTestDriver = setupTopology(applicationConfigFileName);
    try (topologyTestDriver) {
      if (name != null && appConf.getPipeline().getSteps().get(name) == null) {
        logger.info(
            "In env: '{}', No pipeline defined for input test files: '{}', skipping test",
            env,
            name);
        return;
      }
      var inputFiles = extractFiles(testCase, "-input.json");
      var outputFiles = extractFiles(testCase, "-output.json");
      var dlqFiles = extractFiles(testCase, "-dlq.json");
      var discardFiles = extractFiles(testCase, "-discard.json");
      // Sort the input test cases by their index to run them in the correct order
      var inputList =
          inputFiles.entrySet().stream()
              .sorted(Comparator.comparingInt(Map.Entry::getKey))
              .toList();
      for (var inputEntry : inputList) {
        Optional<Path> outputFile =
            outputFiles.containsKey(inputEntry.getKey())
                ? Optional.of(outputFiles.get(inputEntry.getKey()))
                : Optional.empty();
        Optional<Path> dlqFile =
            dlqFiles.containsKey(inputEntry.getKey())
                ? Optional.of(dlqFiles.get(inputEntry.getKey()))
                : Optional.empty();
        Optional<Path> discardFile =
            discardFiles.containsKey(inputEntry.getKey())
                ? Optional.of(discardFiles.get(inputEntry.getKey()))
                : Optional.empty();
        testCase(
            name == null
                ? null
                : outputTopics.get(appConf.getPipeline().getSteps().get(name).getOutputTopic()),
            inputEntry.getValue(),
            outputFile,
            dlqFile,
            discardFile);
      }
    }
  }
}
