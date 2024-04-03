package com.swisscom.daisy.cosmos.candyfloss.transformations.match;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swisscom.daisy.cosmos.candyfloss.testutils.JsonUtil;
import com.swisscom.daisy.cosmos.candyfloss.transformations.match.exceptions.InvalidMatchConfiguration;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MatchBuilderTest {
  @Test
  public void testMatchJsonPath() throws IOException, InvalidMatchConfiguration {
    Map<String, Object> config =
        Map.of(
            "jsonpath", "$.telemetry_data.encoding_path",
            "value", "openconfig-interfaces:interfaces");
    var match = MatchBuilder.fromJson(config, "t1");
    var input =
        JsonUtil.readJson(
            getClass().getClassLoader().getResource("openconfig-interfaces/input.json"));
    assertTrue(match.match(input));
  }

  @Test
  public void testMatchConstant() throws IOException, InvalidMatchConfiguration {
    Map<String, Object> configTrue = Map.of("constant", true);
    Map<String, Object> configFalse = Map.of("constant", false);
    var matchTrue = MatchBuilder.fromJson(configTrue, "t1");
    var matchFalse = MatchBuilder.fromJson(configFalse, "t1");
    var input =
        JsonUtil.readJson(
            getClass().getClassLoader().getResource("openconfig-interfaces/input.json"));
    assertTrue(matchTrue.match(input));
    assertFalse(matchFalse.match(input));
  }

  @Test
  public void testMatchAnd() throws IOException, InvalidMatchConfiguration {
    Map<String, Object> configTrue =
        Map.of(
            "and",
            List.of(
                Map.of(
                    "jsonpath",
                    "$.telemetry_data.encoding_path",
                    "value",
                    "openconfig-interfaces:interfaces")));
    Map<String, Object> configFalse =
        Map.of(
            "and",
            List.of(
                Map.of(
                    "jsonpath",
                    "$.telemetry_data.encoding_path",
                    "value",
                    "openconfig-interfaces:interfaces"),
                Map.of("constant", false)));
    var matchTrue = MatchBuilder.fromJson(configTrue, "t1");
    var matchFalse = MatchBuilder.fromJson(configFalse, "t1");
    var input =
        JsonUtil.readJson(
            getClass().getClassLoader().getResource("openconfig-interfaces/input.json"));
    assertTrue(matchTrue.match(input));
    assertFalse(matchFalse.match(input));
  }

  @Test
  public void testMatchOr() throws IOException, InvalidMatchConfiguration {
    Map<String, Object> configTrue =
        Map.of(
            "and",
            List.of(
                Map.of(
                    "jsonpath",
                    "$.telemetry_data.encoding_path",
                    "value",
                    "openconfig-interfaces:interfaces")));
    Map<String, Object> configFalse =
        Map.of("and", List.of(Map.of("constant", false), Map.of("constant", false)));
    var matchTrue = MatchBuilder.fromJson(configTrue, "t1");
    var matchFalse = MatchBuilder.fromJson(configFalse, "t1");
    var input =
        JsonUtil.readJson(
            getClass().getClassLoader().getResource("openconfig-interfaces/input.json"));
    assertTrue(matchTrue.match(input));
    assertFalse(matchFalse.match(input));
  }
}
