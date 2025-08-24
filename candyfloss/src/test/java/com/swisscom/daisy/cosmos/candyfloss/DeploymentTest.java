package com.swisscom.daisy.cosmos.candyfloss;

import com.swisscom.daisy.cosmos.candyfloss.config.exceptions.InvalidConfigurations;
import com.swisscom.daisy.cosmos.candyfloss.transformations.match.exceptions.InvalidMatchConfiguration;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.json.JSONException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DeploymentTest extends AbstractDeploymentTest {
  @ParameterizedTest
  @MethodSource("providerForAllYangModels")
  protected void test(String applicationConfigFileName, String env, String name, Path testCase)
      throws InvalidConfigurations, IOException, JSONException, InvalidMatchConfiguration {
    testImpl(applicationConfigFileName, env, name, testCase);
  }

  protected static Stream<Arguments> providerForAllYangModels()
          throws IOException, URISyntaxException {
    var outLegacy = providerForYangModels("legacy");
    var outIetf = providerForYangModels("ietf");

    return Stream.concat(outLegacy, outIetf);
  }

  /*** Discover YANG model test cases and provide them as arguments to the parametrized test */
  protected static Stream<Arguments> providerForYangModels(String profile)
      throws IOException, URISyntaxException {
    ClassLoader loader = Thread.currentThread().getContextClassLoader();

    // Create a map from the discovered folders in the `test/resources/deployment` folder
    // The key is the sub-folder name and the value is a full path to the sub folder
    String deploymentFolder =
            profile.equals("ietf") ? "deployment-ietf-telemetry-message" : "deployment";
    var yangModels =
        Files.list(Path.of(Objects.requireNonNull(loader.getResource(deploymentFolder)).toURI()))
            .filter(Files::isDirectory)
            .collect(Collectors.toMap(k -> k.getFileName().toString(), v -> v));

    /// Generate a test argument for each discovered sub folder
    List<Arguments> testCases = new ArrayList<>();
    for (var yangEntry : yangModels.entrySet()) {
      var name = yangEntry.getKey();
      var path = yangEntry.getValue();
      if (path.toFile().getName().endsWith(DISCARD_TEST)) {
        // The discarded tests is for message with no configured pipeline
        // hence we set the name to null
        name = null;
      }
      var testFixtures = Files.list(path).filter(Files::isDirectory).toList();
      for (var testPath : testFixtures) {
        for (var env : List.of("dev", "test", "prod")) {
          String fileNamePrefix = profile.equals("ietf") ? "application.ietf." : "application.";

          String applicationConfig = fileNamePrefix + env + ".conf";
          if (loader.getResource(applicationConfig) != null)
            testCases.add(Arguments.of(applicationConfig, env, name, testPath));
        }
      }
    }
    return testCases.stream();
  }
}
