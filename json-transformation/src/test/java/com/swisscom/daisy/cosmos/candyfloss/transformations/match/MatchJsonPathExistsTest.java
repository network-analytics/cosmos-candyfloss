package com.swisscom.daisy.cosmos.candyfloss.transformations.match;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jayway.jsonpath.JsonPath;
import com.swisscom.daisy.cosmos.candyfloss.testutils.JsonUtil;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class MatchJsonPathExistsTest {
  @Test
  public void testMatch() throws IOException {
    var jsonPath = JsonPath.compile("$.telemetry_data.encoding_path");
    var match = new MatchJsonPathExists(jsonPath, "t1");
    var input =
        JsonUtil.readJson(
            getClass().getClassLoader().getResource("openconfig-interfaces/input.json"));
    assertTrue(match.match(input));
  }

  @Test
  public void testNotMatch() throws IOException {
    var jsonPath = JsonPath.compile("$.telemetry_data.encoding_path_no_exist");
    var match = new MatchJsonPathExists(jsonPath, "t1");
    var input =
        JsonUtil.readJson(
            getClass().getClassLoader().getResource("openconfig-interfaces/input.json"));
    assertFalse(match.match(input));
  }

  @Test
  public void testMatchArray() throws IOException {
    var jsonPathAAA =
        JsonPath.compile(
            "$.telemetry_data.ietf-notification:notification.ietf-yang-push:push-update.datastore-contents.huawei-bras-user-manage:bras-user-manage.access-tables.access-table[*].access-user-aaa-info");
    var matchAAA = new MatchJsonPathExists(jsonPathAAA, "AAA");

    var jsonPathBasic =
        JsonPath.compile(
            "$.telemetry_data.ietf-notification:notification.ietf-yang-push:push-update.datastore-contents.huawei-bras-user-manage:bras-user-manage.access-tables.access-table[*].access-user-basic-info");

    var matchBasic = new MatchJsonPathExists(jsonPathBasic, "BASIC");

    var inputAAA =
        JsonUtil.readJson(
            getClass().getClassLoader().getResource("hu-bng-access-user-aaa-info.json"));
    var inputBasic =
        JsonUtil.readJson(
            getClass().getClassLoader().getResource("hu-bng-access-user-basic-info.json"));

    // Should match, since there are values
    assertTrue(matchAAA.match(inputAAA));
    // Returns empty array, so it's not a match
    assertFalse(matchAAA.match(inputBasic));

    // Should match, since there are values
    assertTrue(matchBasic.match(inputBasic));
    // Returns empty array, so it's not a match
    assertFalse(matchBasic.match(inputAAA));
  }
}
