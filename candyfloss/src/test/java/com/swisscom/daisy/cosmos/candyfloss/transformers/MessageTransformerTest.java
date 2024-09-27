package com.swisscom.daisy.cosmos.candyfloss.transformers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verify;

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
import java.util.Map;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MessageTransformerTest {
  private static final Configuration configuration =
      Configuration.builder().options(Option.SUPPRESS_EXCEPTIONS).build();

  private final ObjectMapper objectMapper =
      new ObjectMapper().configure(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS, true);
  @Mock private ProcessorContext context;
  private MessageTransformer transformer;

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

    transformer = new MessageTransformer(pipelineConfig);
    transformer.init(context);

    transformer.transform(inputKey, inputContext);

    ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<ValueErrorMessage> valueCaptor =
        ArgumentCaptor.forClass(ValueErrorMessage.class);
    verify(context).forward(keyCaptor.capture(), valueCaptor.capture());

    assertEquals(1, valueCaptor.getAllValues().size());
    assertEquals(expectedKey, keyCaptor.getValue());
    assertFalse(valueCaptor.getValue().isError());
    assertEquals(
        objectMapper.writeValueAsString(expected),
        objectMapper.writeValueAsString(
            ((TransformedMessage) valueCaptor.getAllValues().get(0).getValue())
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

    transformer = new MessageTransformer(pipelineConfig);
    transformer.init(context);

    transformer.transform(inputKey, inputContext);

    ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<ValueErrorMessage> valueCaptor =
        ArgumentCaptor.forClass(ValueErrorMessage.class);
    verify(context, times(2)).forward(keyCaptor.capture(), valueCaptor.capture());

    var out = valueCaptor.getAllValues();

    // only 2 outputs have values
    assertEquals(2, out.size());
    assertEquals(expectedKey, keyCaptor.getValue());
    assertFalse(valueCaptor.getValue().isError());

    out.forEach(
        o -> {
          var value =
              ((TransformedMessage) o.getValue())
                  .getValue().stream().map(WriteContext::json).toList();
          var tag = ((TransformedMessage) o.getValue()).getTag();
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

    transformer = new MessageTransformer(pipelineConfig);
    transformer.init(context);

    transformer.transform(inputKey, inputContext);

    ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<ValueErrorMessage> valueCaptor =
        ArgumentCaptor.forClass(ValueErrorMessage.class);
    verify(context).forward(keyCaptor.capture(), valueCaptor.capture());

    assertEquals(1, valueCaptor.getAllValues().size());
    assertEquals(expectedKey, keyCaptor.getValue());
    assertFalse(valueCaptor.getValue().isError());
    assertEquals(
        objectMapper.writeValueAsString(expected),
        objectMapper.writeValueAsString(
            ((TransformedMessage) valueCaptor.getAllValues().get(0).getValue())
                .getValue().stream().map(WriteContext::json).toList()));
  }
}
