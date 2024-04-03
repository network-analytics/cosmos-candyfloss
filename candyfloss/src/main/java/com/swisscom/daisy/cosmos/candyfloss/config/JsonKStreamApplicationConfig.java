package com.swisscom.daisy.cosmos.candyfloss.config;

import com.swisscom.daisy.cosmos.candyfloss.config.exceptions.InvalidConfigurations;
import com.swisscom.daisy.cosmos.candyfloss.testutils.JsonUtil;
import com.swisscom.daisy.cosmos.candyfloss.transformations.match.exceptions.InvalidMatchConfiguration;
import com.typesafe.config.Config;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import lombok.AllArgsConstructor;
import lombok.Getter;

/***
 * The main class to parse and represent the application configs read from `application.$env.conf`
 */
@Getter
@AllArgsConstructor
public class JsonKStreamApplicationConfig {

  public enum InputType {
    JSON,
    AVRO,
  }

  private final Properties kafkaProperties;
  private final String inputTopicName;
  private final InputType inputType;
  private final String discardTopicName;
  private final String dlqTopicName;
  private final String stateStoreName;
  private final long maxCounterCacheAge;
  private final int intCounterWrapAroundLimit;
  private final long longCounterWrapAroundLimit;
  private final long counterWrapAroundTimeMs;
  private final Duration oldCountersScanFrequency;
  private final List<Map<String, Object>> preTransform;
  private final PipelineConfig pipeline;

  public static JsonKStreamApplicationConfig fromConfig(Config config)
      throws IOException, InvalidConfigurations, InvalidMatchConfiguration {
    Properties kafka = new Properties();
    Config kafkaConfig = config.getConfig("kafka");
    for (var entry : kafkaConfig.entrySet()) {
      kafka.put(entry.getKey(), kafkaConfig.getString(entry.getKey()));
    }
    final String inputTopicName = config.getConfig("kstream").getString("input.topic.name");
    final InputType inputType;
    if (config.hasPath("kstream.input.type")) {
      inputType = InputType.valueOf(config.getString("kstream.input.type"));
    } else {
      inputType = InputType.JSON;
    }
    final String discardTopicName = config.getConfig("kstream").getString("discard.topic.name");
    final String dlqTopicName = config.getConfig("kstream").getString("dlq.topic.name");
    final String stateStoreName = config.getConfig("kstream").getString("state.store.name");
    final long maxCounterCacheAge =
        config.getConfig("kstream").getLong("state.store.max.counter.cache.age");
    final int intCounterWrapAroundLimit =
        config.getConfig("kstream").getInt("state.store.int.counter.wrap.limit");
    final long longCounterWrapAroundLimit =
        config.getConfig("kstream").getLong("state.store.long.counter.wrap.limit");
    final long counterWrapAroundTimeMs =
        config.getConfig("kstream").getLong("state.store.long.counter.time.ms");
    final long scanFrequencyDays =
        config.getConfig("kstream").getLong("state.store.delete.scan.frequency.days");
    final Duration oldCountersScanFrequency = Duration.ofDays(scanFrequencyDays);
    final List<Map<String, Object>> preTransform;
    if (config.getConfig("kstream").hasPath("pre.transform")) {
      final String preTransformPath = config.getConfig("kstream").getString("pre.transform");
      preTransform =
          JsonUtil.readJsonArray(
              JsonKStreamApplicationConfig.class
                  .getClassLoader()
                  .getResourceAsStream(preTransformPath));
    } else {
      preTransform = List.of();
    }
    final Config pipelineConfigs = config.getConfig("kstream").getConfig("pipeline");
    var pipeline = PipelineConfig.fromConfig(pipelineConfigs);
    return new JsonKStreamApplicationConfig(
        kafka,
        inputTopicName,
        inputType,
        discardTopicName,
        dlqTopicName,
        stateStoreName,
        maxCounterCacheAge,
        intCounterWrapAroundLimit,
        longCounterWrapAroundLimit,
        counterWrapAroundTimeMs,
        oldCountersScanFrequency,
        preTransform,
        pipeline);
  }
}
