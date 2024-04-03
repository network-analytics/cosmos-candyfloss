package com.swisscom.daisy.cosmos.candyfloss.transformations.jolt;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bazaarvoice.jolt.Chainr;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonStringToJsonTest {

  private Map<String, Object> inputFlat = new HashMap<>(Map.of("k1", "{\"encoded\": 1}"));
  private Map<String, Object> inputNested =
      new HashMap<>(Map.of("k1", new HashMap<>(Map.of("k2", "{\"encoded\": 1}"))));
  private final String operation =
      "com.swisscom.daisy.cosmos.candyfloss.transformations.jolt.DaisyModifier$Overwritr";

  @Test
  void testFlat() {
    Map<String, Object> inputFlat1 = new HashMap<>(Map.of("k1", "{\"encoded\": 1}"));
    Map<String, Object> inputFlat2 = new HashMap<>(Map.of("k1", "{\"encoded\": 1}"));
    var correctSpec =
        Chainr.fromSpec(
            List.of(
                Map.of(
                    "operation", operation, "spec", Map.of("k1", "=jsonStringToJson(@(1,k1))"))));
    var invalidSpec =
        Chainr.fromSpec(
            List.of(
                Map.of(
                    "operation", operation, "spec", Map.of("k2", "=jsonStringToJson(@(1,k2))"))));

    var ret1 = correctSpec.transform(inputFlat1);
    var ret2 = invalidSpec.transform(inputFlat2);

    assertEquals(Map.of("k1", Map.of("encoded", 1)), ret1);
    assertEquals(Map.of("k1", "{\"encoded\": 1}"), ret2);
  }

  @Test
  void testNested() {
    Map<String, Object> inputNested1 =
        new HashMap<>(Map.of("k1", new HashMap<>(Map.of("k2", "{\"encoded\": 1}"))));
    Map<String, Object> inputNested2 =
        new HashMap<>(Map.of("k1", new HashMap<>(Map.of("k2", "{\"encoded\": 1}"))));
    var correctSpec =
        Chainr.fromSpec(
            List.of(
                Map.of(
                    "operation",
                    operation,
                    "spec",
                    Map.of("k1", Map.of("k2", "=jsonStringToJson(@(1,k2))")))));
    var invalidSpec =
        Chainr.fromSpec(
            List.of(
                Map.of(
                    "operation",
                    operation,
                    "spec",
                    Map.of("k1", Map.of("k2", "=jsonStringToJson(@(1,k3))")))));

    var ret1 = correctSpec.transform(inputNested1);
    var ret2 = invalidSpec.transform(inputNested2);

    assertEquals(Map.of("k1", Map.of("k2", Map.of("encoded", 1))), ret1);
    assertEquals(Map.of("k1", Map.of("k2", "{\"encoded\": 1}")), ret2);
  }

  @Test
  void testList() {
    Map<String, Object> inputFlat1 = new HashMap<>(Map.of("k1", " [1, 2]"));
    Map<String, Object> inputFlat2 = new HashMap<>(Map.of("k1", " [1, 2]"));
    var correctSpec =
        Chainr.fromSpec(
            List.of(
                Map.of(
                    "operation", operation, "spec", Map.of("k1", "=jsonStringToJson(@(1,k1))"))));
    var invalidSpec =
        Chainr.fromSpec(
            List.of(
                Map.of(
                    "operation", operation, "spec", Map.of("k1", "=jsonStringToJson(@(1,k3))"))));

    var ret1 = correctSpec.transform(inputFlat1);
    var ret2 = invalidSpec.transform(inputFlat2);

    assertEquals(Map.of("k1", List.of(1, 2)), ret1);
    assertEquals(Map.of("k1", " [1, 2]"), ret2);
  }

  @Test
  void testNotJson() {
    Map<String, Object> inputFlat1 = new HashMap<>(Map.of("k1", "NotJsonString"));
    var correctSpec =
        Chainr.fromSpec(
            List.of(
                Map.of(
                    "operation", operation, "spec", Map.of("k1", "=jsonStringToJson(@(1,k1))"))));

    var ret1 = correctSpec.transform(inputFlat1);

    assertEquals(Map.of("k1", "NotJsonString"), ret1);
  }
}
