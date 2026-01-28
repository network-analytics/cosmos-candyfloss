package com.swisscom.daisy.cosmos.candyfloss.transformations.jolt;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bazaarvoice.jolt.Chainr;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConvertIsoToEpochTest {

    private static final String operation =
            "com.swisscom.daisy.cosmos.candyfloss.transformations.jolt.DaisyModifier$Overwritr";

    @Test
    void testValidIsoTimestamp() {
        // 2026-03-03T16:03:40.020Z
        // Instant.parse("2026-03-03T16:03:40.020Z").getEpochSecond() -> 1772553820
        // .getNano() -> 20000000
        // 20000000 / 1000 = 20000 -> 020000 (microseconds)
        String isoTimestamp = "2026-03-03T16:03:40.020Z";
        String expectedEpoch = "1772553820.020000";

        Map<String, Object> input = new HashMap<>();
        input.put("timestamp", isoTimestamp);

        var chainr =
                Chainr.fromSpec(
                        List.of(
                                Map.of(
                                        "operation",
                                        operation,
                                        "spec",
                                        Map.of("timestamp", "=convert_iso_to_epoch(@(1,timestamp))"))));

        @SuppressWarnings("unchecked")
        Map<String, Object> output = (Map<String, Object>) chainr.transform(input);

        assertEquals(expectedEpoch, output.get("timestamp"));
    }

    @Test
    void testValidIsoTimestampWithMicroseconds() {
        // 1970-01-01T00:00:01.000001Z
        String isoTimestamp = "1970-01-01T00:00:01.000001Z";
        String expectedEpoch = "1.000001";

        Map<String, Object> input = new HashMap<>();
        input.put("timestamp", isoTimestamp);

        var chainr =
                Chainr.fromSpec(
                        List.of(
                                Map.of(
                                        "operation",
                                        operation,
                                        "spec",
                                        Map.of("timestamp", "=convert_iso_to_epoch(@(1,timestamp))"))));

        @SuppressWarnings("unchecked")
        Map<String, Object> output = (Map<String, Object>) chainr.transform(input);

        assertEquals(expectedEpoch, output.get("timestamp"));
    }

    @Test
    void testInvalidTimestamp() {
        String invalidTimestamp = "not-a-timestamp";
        Map<String, Object> input = new HashMap<>();
        input.put("timestamp", invalidTimestamp);

        var chainr =
                Chainr.fromSpec(
                        List.of(
                                Map.of(
                                        "operation",
                                        operation,
                                        "spec",
                                        Map.of("timestamp", "=convert_iso_to_epoch(@(1,timestamp))"))));

        @SuppressWarnings("unchecked")
        Map<String, Object> output = (Map<String, Object>) chainr.transform(input);

        // Should remain unchanged because the function returns Optional.empty()
        assertEquals(invalidTimestamp, output.get("timestamp"));
    }

    @Test
    void testNullInput() {
        Map<String, Object> input = new HashMap<>();
        input.put("timestamp", null);

        var chainr =
                Chainr.fromSpec(
                        List.of(
                                Map.of(
                                        "operation",
                                        operation,
                                        "spec",
                                        Map.of("timestamp", "=convert_iso_to_epoch(@(1,timestamp))"))));

        @SuppressWarnings("unchecked")
        Map<String, Object> output = (Map<String, Object>) chainr.transform(input);

        assertEquals(null, output.get("timestamp"));
    }
}
