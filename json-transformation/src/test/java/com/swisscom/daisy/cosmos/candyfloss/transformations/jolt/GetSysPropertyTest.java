package com.swisscom.daisy.cosmos.candyfloss.transformations.jolt;

import com.bazaarvoice.jolt.Chainr;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GetSysPropertyTest {
    private static final String operation =
            "com.swisscom.daisy.cosmos.candyfloss.transformations.jolt.DaisyModifier$Overwritr";
    static Map<String, Object> input = new HashMap<>(Map.of("k1", "to_be_changed"));

    @BeforeEach
    public void setUp() {
        System.setProperty("ENV_VAR", "xx");
    }

    @Test
    void testNull() {
        var spec = Chainr.fromSpec(
                List.of(Map.of("operation", operation, "spec",
                        Map.of("k1", "=getSysProperty(ENV_VAR_NON_EXISTING)"))));
        var output = spec.transform(input);

        Map<String, Object> outExpected = new HashMap<>();
        outExpected.put("k1", null);

        assertEquals(outExpected, output);
    }

    @Test
    void testNotNull() {
        var spec = Chainr.fromSpec(
                List.of(Map.of("operation", operation, "spec",
                        Map.of("k1", "=getSysProperty(ENV_VAR)"))));
        var output = spec.transform(input);

        Map<String, Object> outExpected = new HashMap<>();
        outExpected.put("k1", "xx");

        assertEquals(outExpected, output);
    }
}
