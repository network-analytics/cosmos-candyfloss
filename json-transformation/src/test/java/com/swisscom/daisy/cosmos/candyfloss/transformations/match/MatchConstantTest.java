package com.swisscom.daisy.cosmos.candyfloss.transformations.match;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swisscom.daisy.cosmos.candyfloss.testutils.JsonUtil;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class MatchConstantTest {
  @Test
  public void testMatchTrue() throws IOException {
    var match = new MatchTrue("t1");
    var input =
        JsonUtil.readJson(
            getClass().getClassLoader().getResource("openconfig-interfaces/input.json"));
    assertTrue(match.match(input));
  }

  @Test
  public void testMatchFalse() throws IOException {
    var match = new MatchFalse("t1");
    var input =
        JsonUtil.readJson(
            getClass().getClassLoader().getResource("openconfig-interfaces/input.json"));
    assertFalse(match.match(input));
  }
}
