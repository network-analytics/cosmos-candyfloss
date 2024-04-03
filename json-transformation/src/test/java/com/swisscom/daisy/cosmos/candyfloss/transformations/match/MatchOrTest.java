package com.swisscom.daisy.cosmos.candyfloss.transformations.match;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swisscom.daisy.cosmos.candyfloss.testutils.JsonUtil;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class MatchOrTest {
  @Test
  public void testMatch() throws IOException {
    var matchTrue = new MatchOr(List.of(new MatchTrue("t1"), new MatchFalse("t1")), "t1");
    var matchFalse = new MatchOr(List.of(new MatchFalse("t1"), new MatchFalse("t1")), "t1");
    var input =
        JsonUtil.readJson(
            getClass().getClassLoader().getResource("openconfig-interfaces/input.json"));
    assertTrue(matchTrue.match(input));
    assertFalse(matchFalse.match(input));
  }
}
