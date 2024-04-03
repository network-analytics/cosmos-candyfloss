package com.swisscom.daisy.cosmos.candyfloss.config;

import com.swisscom.daisy.cosmos.candyfloss.config.exceptions.InvalidConfigurations;
import com.swisscom.daisy.cosmos.candyfloss.transformations.match.exceptions.InvalidMatchConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class NormalizeCountersConfig {
  private final TimeExtractorConfig timeExtractorConfig;
  private List<NormalizeCounterConfig> counterConfigs;

  @SuppressWarnings("unchecked")
  public static NormalizeCountersConfig fromJson(
      Map<String, Object> configs, String normalizeConfigTag)
      throws InvalidConfigurations, InvalidMatchConfiguration {

    final TimeExtractorConfig timeExtractorConfig =
        TimeExtractorConfig.fromJson(
            (Map<String, Object>) configs.get("timestamp-extractor"), normalizeConfigTag);
    var rawNormalizeCountersConfigs = (List<Map<String, Object>>) configs.get("counters");
    List<NormalizeCounterConfig> counterConfigs = new ArrayList<>();
    if (rawNormalizeCountersConfigs != null) {
      for (var i = 0; i < rawNormalizeCountersConfigs.size(); i++) {
        counterConfigs.add(
            NormalizeCounterConfig.fromJson(
                rawNormalizeCountersConfigs.get(i), normalizeConfigTag));
      }
    }
    return new NormalizeCountersConfig(timeExtractorConfig, counterConfigs);
  }
}
