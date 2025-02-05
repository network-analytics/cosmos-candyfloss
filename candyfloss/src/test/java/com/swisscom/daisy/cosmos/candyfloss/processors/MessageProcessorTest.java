package com.swisscom.daisy.cosmos.candyfloss.processors;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.*;
import com.swisscom.daisy.cosmos.candyfloss.config.PipelineConfig;
import com.swisscom.daisy.cosmos.candyfloss.config.exceptions.InvalidConfigurations;
import com.swisscom.daisy.cosmos.candyfloss.messages.ValueErrorMessage;
import com.swisscom.daisy.cosmos.candyfloss.testutils.JsonUtil;
import com.swisscom.daisy.cosmos.candyfloss.transformations.TransformedMessage;
import com.swisscom.daisy.cosmos.candyfloss.transformations.match.exceptions.InvalidMatchConfiguration;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MessageProcessorTest {
  private static final Configuration configuration =
      Configuration.builder().options(Option.SUPPRESS_EXCEPTIONS).build();

  private final ObjectMapper objectMapper =
      new ObjectMapper().configure(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS, true);
  @Mock private ProcessorContext<String, ValueErrorMessage<TransformedMessage>> context;

  @Captor
  private ArgumentCaptor<Record<String, ValueErrorMessage<TransformedMessage>>> argumentCaptor;

  private MessageProcessor msgProcessor;

  @Test
  public void testValidTelemetry()
      throws IOException, InvalidConfigurations, InvalidMatchConfiguration {
    var inputKey = "k1";
    var expectedKey = "k1";
    var input =
        JsonUtil.readJson(
            getClass().getClassLoader().getResourceAsStream("transformers/telemetry-decoded.json"));
    DocumentContext inputContext = JsonPath.using(configuration).parse(input);
    var expected =
        JsonUtil.readJsonArray(
            getClass().getClassLoader().getResource("transformers/telemetry-transformed.json"));

    Config config = ConfigFactory.load("transformers/application.conf");
    var pipelineConfig = PipelineConfig.fromConfig(config.getConfig("kstream.pipeline"));

    msgProcessor = new MessageProcessor(pipelineConfig);
    msgProcessor.init(context);

    msgProcessor.process(new Record<>(inputKey, inputContext, 0L));

    Mockito.verify(context, Mockito.times(1)).forward(argumentCaptor.capture());

    List<Record<String, ValueErrorMessage<TransformedMessage>>> output =
        argumentCaptor.getAllValues();

    assertEquals(1, output.size());

    var processedKey = output.get(0).key();
    assertEquals(expectedKey, processedKey);

    var processedValue = output.get(0).value();
    assertFalse(processedValue.isError());
    assertEquals(
        objectMapper.writeValueAsString(expected),
        objectMapper.writeValueAsString(
            ((TransformedMessage) processedValue.getValue())
                .getValue().stream().map(WriteContext::json).toList()));
  }

  @Test
  public void testMultiValidTelemetry()
      throws IOException, InvalidConfigurations, InvalidMatchConfiguration {
    var inputKey = "k1";
    var expectedKey = "k1";
    var input =
        JsonUtil.readJson(
            getClass()
                .getClassLoader()
                .getResourceAsStream("transformers/telemetry-multi-match-input.json"));
    DocumentContext inputContext = JsonPath.using(configuration).parse(input);
    var expectedValues =
        JsonUtil.readJson(
            getClass()
                .getClassLoader()
                .getResource("transformers/telemetry-multi-match-output.json"));

    Config config = ConfigFactory.load("transformers/application-multi-match.conf");
    var pipelineConfig = PipelineConfig.fromConfig(config.getConfig("kstream.pipeline"));

    msgProcessor = new MessageProcessor(pipelineConfig);
    msgProcessor.init(context);

    msgProcessor.process(new Record<>(inputKey, inputContext, 0L));

    Mockito.verify(context, Mockito.times(2)).forward(argumentCaptor.capture());

    List<Record<String, ValueErrorMessage<TransformedMessage>>> output =
        argumentCaptor.getAllValues();

    assertEquals(2, output.size());

    output.forEach(
        o -> {
          var value =
              ((TransformedMessage) o.value().getValue())
                  .getValue().stream().map(WriteContext::json).toList();
          var tag = ((TransformedMessage) o.value().getValue()).getTag();
          var expectedValue = expectedValues.getOrDefault(tag, null);
          assertNotNull(expectedValue);
          assertEquals(expectedValue, value);
        });
  }

  @Test
  public void testValidDumpInit()
      throws IOException, JSONException, InvalidConfigurations, InvalidMatchConfiguration {
    var inputKey = "k1";
    var expectedKey = "k1";
    var input =
        JsonUtil.readJson(
            getClass().getClassLoader().getResourceAsStream("transformers/dump-init-input.json"));
    DocumentContext inputContext = JsonPath.using(configuration).parse(input);
    var expected =
        objectMapper.readValue(
            JsonUtil.readFromInputStream(
                getClass()
                    .getClassLoader()
                    .getResourceAsStream("transformers/dump-init-transformed.json")),
            Map[].class);
    Config config = ConfigFactory.load("transformers/application.conf");
    var pipelineConfig = PipelineConfig.fromConfig(config.getConfig("kstream.pipeline"));

    msgProcessor = new MessageProcessor(pipelineConfig);
    msgProcessor.init(context);

    msgProcessor.process(new Record<>(inputKey, inputContext, 0L));

    Mockito.verify(context, Mockito.times(1)).forward(argumentCaptor.capture());

    List<Record<String, ValueErrorMessage<TransformedMessage>>> output =
        argumentCaptor.getAllValues();

    assertEquals(1, output.size());

    var processedValue = output.get(0).value();
    assertFalse(processedValue.isError());

    var processedKey = output.get(0).key();
    assertEquals(expectedKey, processedKey);

    assertEquals(
        objectMapper.writeValueAsString(expected),
        objectMapper.writeValueAsString(
            ((TransformedMessage) processedValue.getValue())
                .getValue().stream().map(WriteContext::json).toList()));
  }
}
