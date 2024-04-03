package com.swisscom.daisy.cosmos.candyfloss.transformations.jolt;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bazaarvoice.jolt.Chainr;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MultiplyTest {

  private static final Map<String, Object> inputFlat = new HashMap<>(Map.of("k1", 10));
  private static final Map<String, Object> inputNested =
      new HashMap<>(Map.of("k2", new HashMap<>(Map.of("k3", 30))));
  private static final String operation =
      "com.swisscom.daisy.cosmos.candyfloss.transformations.jolt.DaisyModifier$Overwritr";

  @Test
  void testFlat() {
    var correctSpec =
        Chainr.fromSpec(
            List.of(Map.of("operation", operation, "spec", Map.of("k1", "=multiply(@(1,k1),2)"))));
    var invalidSpec =
        Chainr.fromSpec(
            List.of(Map.of("operation", operation, "spec", Map.of("k2", "=multiply(@(1,k2),2)"))));

    var ret1 = correctSpec.transform(inputFlat);
    var ret2 = invalidSpec.transform(inputFlat);

    assertEquals(Map.of("k1", 20L), ret1);
    assertEquals(inputFlat, ret2);
  }

  @Test
  void testNested() {
    var correctSpec =
        Chainr.fromSpec(
            List.of(
                Map.of(
                    "operation",
                    operation,
                    "spec",
                    Map.of("k2", Map.of("k3", "=multiply(@(1,k3),2)")))));

    var ret1 = correctSpec.transform(inputNested);

    assertEquals(Map.of("k2", Map.of("k3", 60L)), ret1);
  }
}
