package com.swisscom.daisy.cosmos.candyfloss.transformations.match;

import static org.junit.jupiter.api.Assertions.*;

import com.jayway.jsonpath.JsonPath;
import com.swisscom.daisy.cosmos.candyfloss.testutils.JsonUtil;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class MatchJsonPathValueTest {
  @Test
  public void testMatch() throws IOException {
    var jsonPath = JsonPath.compile("$.telemetry_data.encoding_path");
    var value = "openconfig-interfaces:interfaces";
    var match = new MatchJsonPathValue(jsonPath, value, "t1");
    var input =
        JsonUtil.readJson(
            getClass().getClassLoader().getResource("openconfig-interfaces/input.json"));
    assertTrue(match.match(input));
  }

  @Test
  public void testNotMatch() throws IOException {
    var jsonPath = JsonPath.compile("$.telemetry_data.encoding_path");
    var value = "no-existing-path";
    var match = new MatchJsonPathValue(jsonPath, value, "t1");
    var input =
        JsonUtil.readJson(
            getClass().getClassLoader().getResource("openconfig-interfaces/input.json"));
    assertFalse(match.match(input));
  }
}
