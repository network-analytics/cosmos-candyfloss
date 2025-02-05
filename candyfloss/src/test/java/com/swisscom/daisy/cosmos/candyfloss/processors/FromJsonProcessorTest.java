package com.swisscom.daisy.cosmos.candyfloss.processors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.DocumentContext;
import com.swisscom.daisy.cosmos.candyfloss.messages.ValueErrorMessage;
import com.swisscom.daisy.cosmos.candyfloss.testutils.JsonUtil;
import java.io.IOException;
import java.util.List;
import org.apache.kafka.streams.processor.TaskId;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.json.JSONException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.skyscreamer.jsonassert.JSONAssert;

@ExtendWith(MockitoExtension.class)
class FromJsonProcessorTest {
  private final ObjectMapper objectMapper =
      new ObjectMapper().configure(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS, true);
  private FromJsonProcessor jsonProcessor;
  @Mock private ProcessorContext<String, ValueErrorMessage<DocumentContext>> context;
  @Captor ArgumentCaptor<Record<String, ValueErrorMessage<DocumentContext>>> argumentCaptor;
  @Mock private TaskId taskId;

  @BeforeEach
  public void setup() {
    jsonProcessor = new FromJsonProcessor();
    jsonProcessor.init(context);
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
    jsonProcessor.process(new Record<>(inputKey, inputValue, 0L));

    Mockito.verify(context, Mockito.times(1)).forward(argumentCaptor.capture());

    List<Record<String, ValueErrorMessage<DocumentContext>>> output = argumentCaptor.getAllValues();

    assertEquals(1, output.size());
    var processedKey = output.get(0).key();
    assertEquals(expectedKey, processedKey);

    var processedValue = output.get(0).value();
    assertFalse(processedValue.isError());

    JSONAssert.assertEquals(
        expectedValue, objectMapper.writeValueAsString(processedValue.getValue().json()), true);
  }

  @Test
  public void testInvalid() {
    var inputKey = "k1";
    var expectedKey = "k1";
    var inputValue = "{NotValidJson}";
    when(context.taskId()).thenReturn(taskId);
    when(taskId.toString()).thenReturn("mockedTaskId");

    jsonProcessor.process(new Record<>(inputKey, inputValue, 0L));

    Mockito.verify(context, Mockito.times(1)).forward(argumentCaptor.capture());

    List<Record<String, ValueErrorMessage<DocumentContext>>> output = argumentCaptor.getAllValues();

    assertEquals(1, output.size());
    var processedKey = output.get(0).key();
    assertEquals(expectedKey, processedKey);

    var processedValue = output.get(0).value();
    assertTrue(processedValue.isError());
    assertNull(processedValue.getValue());
    var error = processedValue.getErrorMessage();
    assertFalse(error.getErrorMessage().isBlank());
    assertEquals(String.format("\"%s\"", inputValue), error.getFailedValue());
  }
}
