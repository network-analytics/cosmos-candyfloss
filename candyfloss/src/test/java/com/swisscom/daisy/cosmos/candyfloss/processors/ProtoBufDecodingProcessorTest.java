package com.swisscom.daisy.cosmos.candyfloss.processors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swisscom.daisy.cosmos.candyfloss.messages.ValueErrorMessage;
import com.swisscom.daisy.cosmos.candyfloss.testutils.JsonUtil;
import java.io.IOException;
import java.util.List;
import java.util.Map;
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
class ProtoBufDecodingProcessorTest {
  private final ObjectMapper objectMapper =
      new ObjectMapper().configure(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS, true);
  private ProtoBufDecodingProcessor gpbProcessor;

  @Mock private ProcessorContext<String, ValueErrorMessage<Map<String, Object>>> context;

  @Captor
  private ArgumentCaptor<Record<String, ValueErrorMessage<Map<String, Object>>>> argumentCaptor;

  @BeforeEach
  public void setup() {
    gpbProcessor = new ProtoBufDecodingProcessor();
    gpbProcessor.init(context);
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
    gpbProcessor.process(new Record<>(inputKey, inputValue, 0L));

    Mockito.verify(context, Mockito.times(1)).forward(argumentCaptor.capture());

    List<Record<String, ValueErrorMessage<Map<String, Object>>>> output =
        argumentCaptor.getAllValues();

    assertEquals(1, output.size());
    var processedKey = output.get(0).key();
    assertEquals(expectedKey, processedKey);

    var processedValue = output.get(0).value();
    assertFalse(processedValue.isError());

    JSONAssert.assertEquals(
        expectedValue, objectMapper.writeValueAsString(processedValue.getValue()), true);
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

    gpbProcessor.process(new Record<>(inputKey, inputValue, 0L));

    Mockito.verify(context, Mockito.times(1)).forward(argumentCaptor.capture());

    List<Record<String, ValueErrorMessage<Map<String, Object>>>> output =
        argumentCaptor.getAllValues();

    assertEquals(1, output.size());
    var processedKey = output.get(0).key();
    assertEquals(expectedKey, processedKey);

    var processedValue = output.get(0).value();
    assertFalse(processedValue.isError());

    JSONAssert.assertEquals(
        expectedValue, objectMapper.writeValueAsString(processedValue.getValue()), true);
  }
}
