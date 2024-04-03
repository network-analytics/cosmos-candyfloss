package com.swisscom.daisy.cosmos.candyfloss.transformers;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swisscom.daisy.cosmos.candyfloss.config.PipelineConfig;
import com.swisscom.daisy.cosmos.candyfloss.config.exceptions.InvalidConfigurations;
import com.swisscom.daisy.cosmos.candyfloss.testutils.JsonUtil;
import com.swisscom.daisy.cosmos.candyfloss.transformations.match.exceptions.InvalidMatchConfiguration;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.io.IOException;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.skyscreamer.jsonassert.JSONAssert;

@ExtendWith(MockitoExtension.class)
class MessageTransformerTest {
  private final ObjectMapper objectMapper =
      new ObjectMapper().configure(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS, true);
  @Mock private ProcessorContext context;
  private MessageTransformer transformer;

  @Test
  public void testValidTelemetry()
      throws IOException, JSONException, InvalidConfigurations, InvalidMatchConfiguration {
    var inputKey = "k1";
    var expectedKey = "k1";
    var input =
        JsonUtil.readJson(
            getClass().getClassLoader().getResourceAsStream("transformers/telemetry-decoded.json"));
    var expected =
        JsonUtil.readFromInputStream(
            getClass()
                .getClassLoader()
                .getResourceAsStream("transformers/telemetry-transformed.json"));
    Config config = ConfigFactory.load("transformers/application.conf");
    var pipelineConfig = PipelineConfig.fromConfig(config.getConfig("kstream.pipeline"));

    transformer = new MessageTransformer(pipelineConfig);
    var transformed = transformer.transform(inputKey, input);

    assertEquals(expectedKey, transformed.key);
    assertFalse(transformed.value.isError());
    var serialized = objectMapper.writeValueAsString(transformed.value.getValue().getValue());
    JSONAssert.assertEquals(expected, serialized, true);
  }

  @Test
  public void testValidDumpInit()
      throws IOException, JSONException, InvalidConfigurations, InvalidMatchConfiguration {
    var inputKey = "k1";
    var expectedKey = "k1";
    var input =
        JsonUtil.readJson(
            getClass().getClassLoader().getResourceAsStream("transformers/dump-init-input.json"));
    var expected =
        JsonUtil.readFromInputStream(
            getClass()
                .getClassLoader()
                .getResourceAsStream("transformers/dump-init-transformed.json"));
    Config config = ConfigFactory.load("transformers/application.conf");
    var pipelineConfig = PipelineConfig.fromConfig(config.getConfig("kstream.pipeline"));

    transformer = new MessageTransformer(pipelineConfig);
    transformer.init(context);
    var transformed = transformer.transform(inputKey, input);

    assertEquals(expectedKey, transformed.key);
    assertFalse(transformed.value.isError());
    var serialized = objectMapper.writeValueAsString(transformed.value.getValue().getValue());
    JSONAssert.assertEquals(expected, serialized, true);
  }
}
