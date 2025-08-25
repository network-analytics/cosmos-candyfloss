package com.swisscom.daisy.cosmos.candyfloss.transformations.match;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jayway.jsonpath.JsonPath;
import com.swisscom.daisy.cosmos.candyfloss.testutils.JsonUtil;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class MatchNotTest {
  @Test
  public void testMatch() throws IOException {
    var jsonPath1 = JsonPath.compile("$.telemetry_data.encoding_path");
    var jsonPath2 = JsonPath.compile("$.telemetry_data.encoding_path_no_exist");
    var matchTrue = new MatchJsonPathExists(jsonPath1, "t1");
    var matchFalse = new MatchJsonPathExists(jsonPath2, "t1");
    var input =
        JsonUtil.readJson(
            getClass().getClassLoader().getResource("openconfig-interfaces/input.json"));

    var matchNotFalse = new MatchNot(matchTrue, "t1");
    var matchNotTrue = new MatchNot(matchFalse, "t1");

    assertTrue(matchTrue.match(input));
    assertFalse(matchFalse.match(input));
    assertFalse(matchNotFalse.match(input));
    assertTrue(matchNotTrue.match(input));
  }

  @Test
  public void testMatchComplex() throws IOException {
    var matchTrue = new MatchOr(List.of(new MatchTrue("t1"), new MatchFalse("t1")), "t1");
    var matchFalse = new MatchOr(List.of(new MatchFalse("t1"), new MatchFalse("t1")), "t1");
    var matchNotFalse = new MatchNot(matchTrue, "t1");
    var matchNotTrue = new MatchNot(matchFalse, "t1");
    var input =
        JsonUtil.readJson(
            getClass().getClassLoader().getResource("openconfig-interfaces/input.json"));
    assertTrue(matchNotTrue.match(input));
    assertFalse(matchNotFalse.match(input));
  }
}
