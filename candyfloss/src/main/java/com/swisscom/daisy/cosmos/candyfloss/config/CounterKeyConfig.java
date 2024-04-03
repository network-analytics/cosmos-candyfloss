package com.swisscom.daisy.cosmos.candyfloss.config;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CounterKeyConfig {
  private final List<CounterKeyExtractor> keyExtractors;

  public static CounterKeyConfig fromJson(List<Map<String, Object>> configs) {
    var keyExtractors =
        configs.stream()
            .map(
                extractor -> {
                  if (extractor.containsKey("jsonpath")) {
                    return new JsonPathCounterKeyExtractor((String) extractor.get("jsonpath"));
                  } else if (extractor.containsKey("constant")) {
                    return new ConstantCounterKeyExtractor((String) extractor.get("constant"));
                  }
                  return null;
                })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    return new CounterKeyConfig(keyExtractors);
  }
}
