package com.swisscom.daisy.cosmos.candyfloss.transformers;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swisscom.daisy.cosmos.candyfloss.testutils.JsonUtil;
import java.io.IOException;
import org.json.JSONException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.skyscreamer.jsonassert.JSONAssert;

@ExtendWith(MockitoExtension.class)
class ProtoBufDecodingTransformerTest {
  private final ObjectMapper objectMapper =
      new ObjectMapper().configure(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS, true);
  private ProtoBufDecodingTransformer transformer;

  @BeforeEach
  public void setup() {
    transformer = new ProtoBufDecodingTransformer();
  }

  @Test
  public void testValidTelemetry() throws IOException, JSONException {
    var inputKey = "k1";
    var expectedKey = "k1";
    var inputValue =
        JsonUtil.readJson(
            getClass()
                .getClassLoader()
                .getResourceAsStream("transformers/telemetry-deserialized.json"));
    var expectedValue =
        JsonUtil.readFromInputStream(
            getClass().getClassLoader().getResourceAsStream("transformers/telemetry-decoded.json"));
    var transformed = transformer.transform(inputKey, inputValue);
    assertEquals(expectedKey, transformed.key);
    assertFalse(transformed.value.isError());
    JSONAssert.assertEquals(
        expectedValue, objectMapper.writeValueAsString(transformed.value.getValue()), true);
  }

  @Test
  public void testValidDumpInit() throws IOException, JSONException {
    // We want to make sure events that don't have telemetry data are passed as
    var inputKey = "k1";
    var expectedKey = "k1";
    var inputValue =
        JsonUtil.readJson(
            getClass().getClassLoader().getResourceAsStream("transformers/dump-init-input.json"));
    var expectedValue =
        JsonUtil.readFromInputStream(
            getClass().getClassLoader().getResourceAsStream("transformers/dump-init-input.json"));
    var transformed = transformer.transform(inputKey, inputValue);
    assertEquals(expectedKey, transformed.key);
    assertFalse(transformed.value.isError());
    JSONAssert.assertEquals(
        expectedValue, objectMapper.writeValueAsString(transformed.value.getValue()), true);
  }
}
