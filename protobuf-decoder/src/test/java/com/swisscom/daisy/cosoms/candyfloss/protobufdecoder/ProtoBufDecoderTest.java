package com.swisscom.daisy.cosoms.candyfloss.protobufdecoder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swisscom.daisy.cosmos.candyfloss.testutils.JsonUtil;
import java.io.*;
import java.time.Duration;
import java.time.Instant;
import org.json.JSONException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;

class ProtoBufDecoderTest {
  private final ObjectMapper objectMapper = new ObjectMapper();

  /*** Each test is a folder that contains two files `input.json` and `expected.json` */
  public void testDecode(String directory) throws IOException, JSONException {
    var input =
        JsonUtil.readJson(getClass().getClassLoader().getResource(directory + "/input.json"));
    var expected =
        JsonUtil.readJson(getClass().getClassLoader().getResource(directory + "/expected.json"));
    var decoder = new ProtoBufDecoder();
    var output = decoder.decode(input);
    // System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(output));
    JSONAssert.assertEquals(
        objectMapper.writeValueAsString(expected), objectMapper.writeValueAsString(output), true);
  }

  @Test
  public void testDecodeGPBOpenConfigInterface() throws IOException, JSONException {
    testDecode("gpbkv-openconfig-interfaces");
  }

  @Test
  public void testDecodeGPBCiscoNpOperCounters() throws IOException, JSONException {
    testDecode("gpbkv-cisco-io-np-oper-counters");
  }

  @Test
  public void testDecodeNoGPB() throws IOException, JSONException {
    testDecode("no-gpb");
  }

  /***
   * This test is disabled because didn't want commit large file to the repository.
   * device-json-raw.json is obtained by dumping samples from `daisy.prod.device-json-raw` Kafka topic.
   */
  @Test
  @Disabled
  public void testDecodeLoad() throws IOException {
    var input =
        JsonUtil.readJsonArray(getClass().getClassLoader().getResource("device-json-raw.json"));
    var decoder = new ProtoBufDecoder();
    var start = Instant.now();
    for (var line : input) {
      var decoded = decoder.decode(line);
    }
    var end = Instant.now();
    var duration = Duration.between(start, end);
    System.err.printf(
        "Parsed %d message in %dms at rate: %.2f msg/sec",
        input.size(),
        duration.toMillis(),
        (double) input.size() / ((double) duration.toMillis() / 1000));
  }
}
