package com.swisscom.daisy.cosmos.candyfloss.transformations.jolt;

import com.bazaarvoice.jolt.Chainr;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConvertIsoToEpochTest {

  private static final String TIMESTAMP_KEY = "timestamp";
  
  private static final String operation =
      "com.swisscom.daisy.cosmos.candyfloss.transformations.jolt.DaisyModifier$Overwritr";

  @SuppressWarnings("unchecked")
  private Map<String, Object> transform(Map<String, Object> in) {
    var chainr = Chainr.fromSpec(
            List.of(
                    Map.of(
                            "operation",
                            operation,
                            "spec",
                            Map.of(TIMESTAMP_KEY, "=isoToEpochSecondsMicros(@(1,timestamp))"))));

    return (Map<String, Object>) chainr.transform(in);
  }

  private void assertTimestamp(Object inputTimestampValue) {
    assertTimestamp(inputTimestampValue, null);
  }

  private void assertTimestamp(Object inputTimestampValue, String expectedEpoch) {
    Map<String, Object> input = new HashMap<>();
    input.put(TIMESTAMP_KEY, inputTimestampValue);

    var out = transform(input);

    assertTrue(out.containsKey(TIMESTAMP_KEY), String.format("Output is missing %s key", TIMESTAMP_KEY));

    if (expectedEpoch == null) {
      if (inputTimestampValue == null) {
        assertNull(out.get(TIMESTAMP_KEY));
      } else {
        assertEquals(inputTimestampValue, out.get(TIMESTAMP_KEY));
      }
    } else {
        assertEquals(expectedEpoch, out.get(TIMESTAMP_KEY));
    }
  }

  @Test
  void testValidIsoTimestamp() {
    assertTimestamp("2026-03-03T16:03:40.020Z", "1772553820.020000");
  }

  @Test
  void testValidIsoTimestampWithMicroseconds() {
    assertTimestamp("1970-01-01T00:00:01.000001Z", "1.000001");
  }

  @Test
  void testValidIsoTimestampWithPositiveOffset() {
    assertTimestamp("2026-01-03T16:03:40.020+01:00", "1767452620.020000");
  }

  @Test
  void testValidIsoTimestampWithNegativeOffset() {
    assertTimestamp("2026-01-03T16:03:40.020-05:00", "1767474220.020000");
  }

  @Test
  void testInvalidAlphabeticTimestamp() {
    assertTimestamp("not-a-timestamp");
  }

  @Test
  void testInvalidNullTimestamp() {
    assertTimestamp(null);
  }

  @Test
  void testInvalidTimeRangeTimestamp() {
    assertTimestamp("2026-01-19T99:99:99.000Z");
  }

  @Test
  void testInvalidNonExistentDayTimestamp() {
    assertTimestamp("2024-02-30T00:00:00Z");
  }

  @Test
  void testInvalidSeparatorTimestamp() {
    assertTimestamp("2026-01-03 16:03:40.020");
  }

  @Test
  void testInvalidTimezoneTimestamp() {
    assertTimestamp("2026-03-03T16:03:40.020");
  }
}
