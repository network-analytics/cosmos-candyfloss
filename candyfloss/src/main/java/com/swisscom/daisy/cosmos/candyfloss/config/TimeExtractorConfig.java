package com.swisscom.daisy.cosmos.candyfloss.config;

import com.jayway.jsonpath.JsonPath;
import com.swisscom.daisy.cosmos.candyfloss.config.exceptions.InvalidConfigurations;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TimeExtractorConfig {
  public enum TimestampType {
    RFC2822,
    EpochMilli,
    EpochSeconds
  }

  private final JsonPath jsonPath;
  private final TimestampType valueType;

  public static TimeExtractorConfig fromJson(Map<String, Object> configs, String normalizeConfigTag)
      throws InvalidConfigurations {
    final JsonPath jsonpath = JsonPath.compile((String) configs.get("jsonpath"));
    final String timestampTypeString = (String) configs.get("timestamp-type");
    final TimestampType timestampType;
    if (timestampTypeString.equals("RFC2822")) {
      timestampType = TimestampType.RFC2822;
    } else if (timestampTypeString.equals("EpochMilli")) {
      timestampType = TimestampType.EpochMilli;
    } else if (timestampTypeString.equals("EpochSeconds")) {
      timestampType = TimestampType.EpochSeconds;
    } else {
      throw new InvalidConfigurations(
          "Invalid timestamp-type type: '"
              + timestampTypeString
              + "' at tag: "
              + normalizeConfigTag);
    }
    return new TimeExtractorConfig(jsonpath, timestampType);
  }
}
