package com.swisscom.daisy.cosmos.candyfloss.config;

import com.swisscom.daisy.cosmos.candyfloss.config.exceptions.InvalidConfigurations;
import com.swisscom.daisy.cosmos.candyfloss.testutils.JsonUtil;
import com.swisscom.daisy.cosmos.candyfloss.transformations.match.exceptions.InvalidMatchConfiguration;
import com.typesafe.config.Config;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PipelineConfig {
  private Map<String, PipelineStepConfig> steps;

  public static PipelineConfig fromConfig(Config configs)
      throws InvalidConfigurations, InvalidMatchConfiguration, IOException {
    Map<String, PipelineStepConfig> steps = new HashMap<>();
    for (var key : configs.root().keySet().stream().sorted().toList()) {
      var config = configs.getConfig(key);
      final String outputTopic = config.getString("output.topic.name");
      final String file = config.getString("file");
      final var resource =
          JsonKStreamApplicationConfig.class.getClassLoader().getResourceAsStream(file);
      if (resource == null) {
        throw new IOException("File doesn't exist: " + file);
      }
      final var pipelineJson = JsonUtil.readJson(resource);
      steps.put(key, PipelineStepConfig.fromJson(outputTopic, pipelineJson, key));
    }
    return new PipelineConfig(steps);
  }
}
