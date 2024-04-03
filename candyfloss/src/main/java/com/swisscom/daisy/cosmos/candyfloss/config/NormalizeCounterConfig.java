package com.swisscom.daisy.cosmos.candyfloss.config;

import com.jayway.jsonpath.JsonPath;
import com.swisscom.daisy.cosmos.candyfloss.config.exceptions.InvalidConfigurations;
import com.swisscom.daisy.cosmos.candyfloss.transformations.match.Match;
import com.swisscom.daisy.cosmos.candyfloss.transformations.match.MatchBuilder;
import com.swisscom.daisy.cosmos.candyfloss.transformations.match.exceptions.InvalidMatchConfiguration;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class NormalizeCounterConfig {
  public enum CounterType {
    U32,
    U64
  }

  private final Match match;
  private final CounterKeyConfig key;
  private final CounterType counterType;
  private final JsonPath valuePath;

  @SuppressWarnings("unchecked")
  public static NormalizeCounterConfig fromJson(
      Map<String, Object> configs, String normalizeConfigTag)
      throws InvalidConfigurations, InvalidMatchConfiguration {
    var match =
        MatchBuilder.fromJson((Map<String, Object>) configs.get("match"), normalizeConfigTag);
    var rawKeyExtractors = (List<Map<String, Object>>) configs.get("key");
    var counterKeyConfig = CounterKeyConfig.fromJson(rawKeyExtractors);
    var valueJsonPath =
        JsonPath.compile((String) ((Map<String, Object>) configs.get("value")).get("jsonpath"));
    var counterTypeString = (String) configs.get("type");
    CounterType counterType;
    if (counterTypeString.equals("u32")) {
      counterType = CounterType.U32;
    } else if (counterTypeString.equals("u64")) {
      counterType = CounterType.U64;
    } else {
      throw new InvalidConfigurations(
          "Invalid counter type: '" + counterTypeString + "' at tag: " + normalizeConfigTag);
    }
    return new NormalizeCounterConfig(match, counterKeyConfig, counterType, valueJsonPath);
  }
}
