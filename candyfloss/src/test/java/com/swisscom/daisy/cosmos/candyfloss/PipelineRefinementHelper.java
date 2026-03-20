package com.swisscom.daisy.cosmos.candyfloss;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.swisscom.daisy.cosmos.candyfloss.config.JsonKStreamApplicationConfig;
import com.swisscom.daisy.cosmos.candyfloss.config.PipelineStepConfig;
import com.swisscom.daisy.cosmos.candyfloss.config.exceptions.InvalidConfigurations;
import com.swisscom.daisy.cosmos.candyfloss.transformations.jolt.CustomFunctions;
import com.swisscom.daisy.cosmos.candyfloss.transformations.match.exceptions.InvalidMatchConfiguration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@SuppressWarnings("NewClassNamingConvention")
class PipelineRefinementHelper extends AbstractDeploymentTest {

  private static Stream<Arguments> gatherInputs() {
    String deployment = System.getProperty("deployment");
    if (deployment == null) {
      deployment = System.getenv("DEPLOYMENT");
    }
    String config = System.getProperty("config");
    if (config == null) {
      config = System.getenv("CONFIG");
    }

    if (deployment != null && config != null) {
      return Stream.of(Arguments.of(deployment, config));
    }

    // TODO: which default?
    return Stream.of(Arguments.of("deployment", "application.dev.conf"));
  }

  @ParameterizedTest
  @MethodSource("gatherInputs")
  protected void pipelineRefinement(String deploymentFolderName, String appConfigFileName)
      throws IOException, InvalidConfigurations, InvalidMatchConfiguration {
    run(deploymentFolderName, appConfigFileName);
  }

  protected void run(String deploymentFolderName, String appConfigFileName)
      throws IOException, InvalidConfigurations, InvalidMatchConfiguration {
    final var topologyTestDriver = setupTopology(appConfigFileName);

    Path deploymentPath = resolveDeploymentPath(deploymentFolderName);

    ObjectMapper mapper = new ObjectMapper(CustomFunctions.factory);
    DefaultPrettyPrinter prettyPrinter = new CustomPrettyPrinter();
    DefaultIndenter indenter = new DefaultIndenter("  ", DefaultIndenter.SYS_LF);
    prettyPrinter.indentArraysWith(indenter);
    prettyPrinter.withObjectIndenter(indenter);
    mapper.setDefaultPrettyPrinter(prettyPrinter);

    try {
      var stringSerde = Serdes.String();
      TestInputTopic<String, String> inputTopic =
          topologyTestDriver.createInputTopic(
              appConf.getInputTopicName(), stringSerde.serializer(), stringSerde.serializer());
      Map<String, TestOutputTopic<String, String>> outputTopics =
          appConf.getPipeline().getSteps().values().stream()
              .collect(
                  Collectors.toMap(
                      PipelineStepConfig::getOutputTopic,
                      step ->
                          topologyTestDriver.createOutputTopic(
                              step.getOutputTopic(),
                              stringSerde.deserializer(),
                              stringSerde.deserializer())));
      TestOutputTopic<String, String> discardTopic =
          topologyTestDriver.createOutputTopic(
              appConf.getDiscardTopicName(),
              stringSerde.deserializer(),
              stringSerde.deserializer());
      TestOutputTopic<String, String> dlqTopic =
          topologyTestDriver.createOutputTopic(
              appConf.getDlqTopicName(), stringSerde.deserializer(), stringSerde.deserializer());

      try (Stream<Path> paths = Files.walk(deploymentPath)) {
        List<Path> inputPaths =
            paths.filter(path -> path.toString().endsWith("-input.json")).sorted().toList();

        for (Path inputPath : inputPaths) {
          processInputFile(
              deploymentPath,
              inputPath,
              mapper,
              appConf,
              inputTopic,
              outputTopics,
              discardTopic,
              dlqTopic);
        }
      }
    } catch (IOException e) {
      // TODO
    }
  }

  protected static Path resolveDeploymentPath(String deploymentFolder) {
    // resolve from src/ instead of build/
    Path path = Paths.get("src", "test", "resources", deploymentFolder);
    if (Files.exists(path) && Files.isDirectory(path)) {
      return path.toAbsolutePath();
    }

    throw new RuntimeException("Resource path not found: " + deploymentFolder);
  }

  private static void processInputFile(
      Path deploymentPath,
      Path inputPath,
      ObjectMapper mapper,
      JsonKStreamApplicationConfig appConf,
      TestInputTopic<String, String> inputTopic,
      Map<String, TestOutputTopic<String, String>> outputTopics,
      TestOutputTopic<String, String> discardTopic,
      TestOutputTopic<String, String> dlqTopic)
      throws IOException {
    String pipelineName = deploymentPath.relativize(inputPath).getName(0).toString();
    PipelineStepConfig pipelineStep = appConf.getPipeline().getSteps().get(pipelineName);
    if (pipelineStep == null) {
      System.out.println(
          "Skipping " + inputPath + ": No pipeline step found for '" + pipelineName + "'");
      return;
    }

    Path outputPath = toOutputPath(inputPath);
    if (!Files.exists(outputPath)) {
      throw new IOException("Output file does not exist: " + outputPath);
    }

    String inputContent = Files.readString(inputPath);
    inputTopic.pipeInput("pipeline-refinement-helper-key", inputContent);

    TestOutputTopic<String, String> outputTopic = outputTopics.get(pipelineStep.getOutputTopic());
    if (outputTopic == null) {
      throw new IOException(
          "Output topic not found for pipeline '"
              + pipelineName
              + "': "
              + pipelineStep.getOutputTopic());
    }

    String currentOutputContent = Files.readString(outputPath);
    JsonNode currentOutputTree = mapper.readTree(currentOutputContent);
    String comments = extractComments(currentOutputContent);

    JsonNode producedOutput = readProducedOutput(mapper, currentOutputTree, outputTopic);

    String newOutputContent;
    // Check if root nodes can be merged; if not, replace it entirely.
    if (canMerge(currentOutputTree, producedOutput)) {
      mergePreservingOrder(currentOutputTree, producedOutput, mapper);
      newOutputContent =
          mapper.writerWithDefaultPrettyPrinter().writeValueAsString(currentOutputTree);
    } else {
      // If types differ (e.g. Object vs Array), we must overwrite the entire file.
      newOutputContent = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(producedOutput);
    }

    if (!comments.isEmpty()) {
      newOutputContent = comments + "\n" + newOutputContent;
    }
    Files.writeString(outputPath, newOutputContent);

    // Drain side topics so records do not accumulate silently across inputs.
    discardTopic.readRecordsToList();
    dlqTopic.readRecordsToList();
    outputTopics.values().forEach(TestOutputTopic::readRecordsToList);
  }

  private static String extractComments(String originalContent) {
    if (originalContent == null || originalContent.isEmpty()) {
      return "";
    }
    StringBuilder comments = new StringBuilder();
    String regex = "\"(\\\\.|[^\"\\\\])*\"|//.*|/\\*[\\s\\S]*?\\*/";
    Pattern pattern = Pattern.compile(regex);
    Matcher matcher = pattern.matcher(originalContent);

    while (matcher.find()) {
      String match = matcher.group();
      if (!match.startsWith("\"")) {
        comments.append(match).append("\n");
      }
    }
    return comments.toString().trim();
  }

  private static Path toOutputPath(Path inputPath) {
    return inputPath.resolveSibling(
        inputPath.getFileName().toString().replace("-input.json", "-output.json"));
  }

  private static JsonNode readProducedOutput(
      ObjectMapper mapper, JsonNode currentOutput, TestOutputTopic<String, String> outputTopic)
      throws IOException {
    List<String> producedValues = outputTopic.readValuesToList();
    if (producedValues.isEmpty()) {
      return currentOutput.isArray() ? mapper.createArrayNode() : mapper.createObjectNode();
    }
    if (currentOutput.isArray()) {
      ArrayNode newArray = mapper.createArrayNode();
      for (String producedValue : producedValues) {
        newArray.add(mapper.readTree(producedValue));
      }
      return newArray;
    }

    return mapper.readTree(producedValues.get(0));
  }

  private static void mergePreservingOrder(
      JsonNode currentNode, JsonNode producedNode, ObjectMapper mapper) {
    if (currentNode.isObject() && producedNode.isObject()) {
      mergeObjectInPlace((ObjectNode) currentNode, (ObjectNode) producedNode, mapper);
    } else if (currentNode.isArray() && producedNode.isArray()) {
      mergeArrayInPlace((ArrayNode) currentNode, (ArrayNode) producedNode, mapper);
    }
  }

  private static boolean canMerge(JsonNode n1, JsonNode n2) {
    return (n1.isObject() && n2.isObject()) || (n1.isArray() && n2.isArray());
  }

  private static void mergeObjectInPlace(
      ObjectNode currentNode, ObjectNode producedNode, ObjectMapper mapper) {
    List<String> currentKeys = new ArrayList<>();
    currentNode.fieldNames().forEachRemaining(currentKeys::add);

    Set<String> producedKeys = new HashSet<>();
    producedNode.fieldNames().forEachRemaining(producedKeys::add);

    // Remove keys from current that are not in produced
    List<String> keysToRemove = new ArrayList<>();
    for (String key : currentKeys) {
      if (!producedKeys.contains(key)) {
        keysToRemove.add(key);
      }
    }
    keysToRemove.forEach(currentNode::remove);

    // Update/Recurse on existing keys
    for (String key : currentKeys) {
      if (producedKeys.contains(key)) {
        JsonNode currentField = currentNode.get(key);
        JsonNode producedField = producedNode.get(key);
        if (currentField.isObject() && producedField.isObject()) {
          mergeObjectInPlace((ObjectNode) currentField, (ObjectNode) producedField, mapper);
        } else if (currentField.isArray() && producedField.isArray()) {
          mergeArrayInPlace((ArrayNode) currentField, (ArrayNode) producedField, mapper);
        } else if (!currentField.equals(producedField)) {
          currentNode.set(key, producedField);
        }
      }
    }

    // Add new keys from produced that were not in current
    for (String key : producedKeys) {
      if (!currentNode.has(key)) {
        currentNode.set(key, producedNode.get(key));
      }
    }
  }

  private static void mergeArrayInPlace(
      ArrayNode currentNode, ArrayNode producedNode, ObjectMapper mapper) {
    int sharedSize = Math.min(currentNode.size(), producedNode.size());

    // Merge existing elements
    for (int i = 0; i < sharedSize; i++) {
      JsonNode currentElement = currentNode.get(i);
      JsonNode producedElement = producedNode.get(i);

      if (canMerge(currentElement, producedElement)) {
        mergePreservingOrder(currentElement, producedElement, mapper);
      } else {
        // If elements can't be merged (e.g. one is int, other is object, or different types),
        // replace it.
        currentNode.set(i, producedElement);
      }
    }

    // Remove extra elements from the end of current
    while (currentNode.size() > producedNode.size()) {
      currentNode.remove(currentNode.size() - 1);
    }

    // Add new elements from the end of produced
    for (int i = sharedSize; i < producedNode.size(); i++) {
      currentNode.add(producedNode.get(i));
    }
  }

  private static class CustomPrettyPrinter extends DefaultPrettyPrinter {
    public CustomPrettyPrinter() {
      super();
      _objectFieldValueSeparatorWithSpaces = ": ";
    }

    public CustomPrettyPrinter(CustomPrettyPrinter base) {
      super(base);
    }

    @Override
    public DefaultPrettyPrinter createInstance() {
      return new CustomPrettyPrinter(this);
    }
  }

  public static void main(String[] args)
      throws IOException, InvalidConfigurations, InvalidMatchConfiguration {
    PipelineRefinementHelper helper = new PipelineRefinementHelper();
    Stream<Arguments> argumentsStream;

    if (args != null && args.length >= 2) {
      argumentsStream = Stream.of(Arguments.of(args[0], args[1]));
    } else {
      argumentsStream = gatherInputs();
    }

    List<Arguments> argsList = argumentsStream.toList();
    for (Arguments arguments : argsList) {
      String deploymentFolderName = arguments.get()[0].toString();
      String appConfigFileName = arguments.get()[1].toString();
      helper.run(deploymentFolderName, appConfigFileName);
    }
  }
}
