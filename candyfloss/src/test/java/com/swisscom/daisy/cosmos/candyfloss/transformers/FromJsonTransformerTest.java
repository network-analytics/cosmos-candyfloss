package com.swisscom.daisy.cosmos.candyfloss.transformers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swisscom.daisy.cosmos.candyfloss.testutils.JsonUtil;
import java.io.IOException;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.processor.TaskId;
import org.json.JSONException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.skyscreamer.jsonassert.JSONAssert;

@ExtendWith(MockitoExtension.class)
class FromJsonTransformerTest {
  private final ObjectMapper objectMapper =
      new ObjectMapper().configure(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS, true);
  private FromJsonTransformer transformer;
  @Mock private ProcessorContext context;
  @Mock private TaskId taskId;

  @BeforeEach
  public void setup() {
    transformer = new FromJsonTransformer();
    transformer.init(context);
  }

  @Test
  public void testValid() throws IOException, JSONException {
    var inputKey = "k1";
    var expectedKey = "k1";
    var inputValue =
        JsonUtil.readFromInputStream(
            getClass().getClassLoader().getResourceAsStream("transformers/telemetry-input.json"));
    var expectedValue =
        JsonUtil.readFromInputStream(
            getClass()
                .getClassLoader()
                .getResourceAsStream("transformers/telemetry-deserialized.json"));
    var transformed = transformer.transform(inputKey, inputValue);
    assertEquals(expectedKey, transformed.key);
    Assertions.assertFalse(transformed.value.isError());
    JSONAssert.assertEquals(
        expectedValue, objectMapper.writeValueAsString(transformed.value.getValue()), true);
  }

  @Test
  public void testInvalid() {
    var inputKey = "k1";
    var expectedKey = "k1";
    var inputValue = "{NotValidJson}";
    when(context.taskId()).thenReturn(taskId);
    when(taskId.toString()).thenReturn("mockedTaskId");

    var transformed = transformer.transform(inputKey, inputValue);

    assertEquals(expectedKey, transformed.key);
    Assertions.assertTrue(transformed.value.isError());
    assertNull(transformed.value.getValue());
    var error = transformed.value.getErrorMessage();
    Assertions.assertFalse(error.getErrorMessage().isBlank());
    Assertions.assertEquals(String.format("\"%s\"", inputValue), error.getFailedValue());
  }
}
