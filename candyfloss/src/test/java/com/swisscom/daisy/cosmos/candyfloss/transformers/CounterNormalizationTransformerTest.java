package com.swisscom.daisy.cosmos.candyfloss.transformers;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swisscom.daisy.cosmos.candyfloss.config.JsonKStreamApplicationConfig;
import com.swisscom.daisy.cosmos.candyfloss.config.exceptions.InvalidConfigurations;
import com.swisscom.daisy.cosmos.candyfloss.messages.FlattenedMessage;
import com.swisscom.daisy.cosmos.candyfloss.messages.ValueErrorMessage;
import com.swisscom.daisy.cosmos.candyfloss.testutils.JsonUtil;
import com.swisscom.daisy.cosmos.candyfloss.transformations.match.exceptions.InvalidMatchConfiguration;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.state.Stores;
import org.apache.kafka.streams.state.ValueAndTimestamp;
import org.json.JSONException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.skyscreamer.jsonassert.JSONAssert;

@ExtendWith(MockitoExtension.class)
class CounterNormalizationTransformerTest {

  private TopologyTestDriver topologyTestDriver;
  private TestInputTopic<String, String> inputTopic;
  private TestOutputTopic<String, String> outputTopic;
  private JsonKStreamApplicationConfig appConf;

  private static final long MESSAGE_TIMESTAMP = 1647244800000L;

  public void setup(String applicationConfig)
      throws IOException, InvalidConfigurations, InvalidMatchConfiguration {
    var stringSerde = Serdes.String();
    Config config = ConfigFactory.load(applicationConfig);
    appConf = JsonKStreamApplicationConfig.fromConfig(config);
    var topology = getTestTopology(appConf);
    topologyTestDriver = new TopologyTestDriver(topology, appConf.getKafkaProperties());
    inputTopic =
        topologyTestDriver.createInputTopic(
            appConf.getInputTopicName(), stringSerde.serializer(), stringSerde.serializer());
    outputTopic =
        topologyTestDriver.createOutputTopic(
            appConf.getPipeline().getSteps().get("testStep").getOutputTopic(),
            stringSerde.deserializer(),
            stringSerde.deserializer());
  }

  @AfterEach
  public void clean() {
    topologyTestDriver.close();
  }

  private Topology getTestTopology(JsonKStreamApplicationConfig appConf) {
    Serde<String> stringSerde = Serdes.String();
    ObjectMapper objectMapper =
        new ObjectMapper().configure(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS, true);
    // Build simple topology for testing
    var builder = new StreamsBuilder();
    var inputStream =
        builder.stream(appConf.getInputTopicName(), Consumed.with(stringSerde, stringSerde));
    var storeBuilder =
        Stores.timestampedKeyValueStoreBuilder(
            Stores.persistentTimestampedKeyValueStore(appConf.getStateStoreName()),
            Serdes.Bytes(),
            Serdes.Bytes());
    builder.addStateStore(storeBuilder);
    var jsonStream = inputStream.transform(FromJsonTransformer::new);
    var flattenedStream =
        jsonStream.mapValues(
            value -> new ValueErrorMessage<>(new FlattenedMessage(value.getValue(), "testStep")));
    var normalized =
        flattenedStream
            .mapValues(ValueErrorMessage::getValue)
            .transform(
                () ->
                    new CounterNormalizationTransformer(
                        appConf.getPipeline(),
                        appConf.getStateStoreName(),
                        appConf.getMaxCounterCacheAge(),
                        appConf.getIntCounterWrapAroundLimit(),
                        appConf.getLongCounterWrapAroundLimit(),
                        appConf.getCounterWrapAroundTimeMs(),
                        Duration.ofMillis(
                            (System.currentTimeMillis() - MESSAGE_TIMESTAMP)
                                + appConf.getMaxCounterCacheAge()),
                        appConf.getOldCountersScanFrequency()),
                Named.as("counterNormalization"),
                appConf.getStateStoreName());
    var outputStream =
        normalized
            .mapValues(ValueErrorMessage::getValue)
            .mapValues(FlattenedMessage::getValue)
            .mapValues(
                value -> {
                  try {
                    return objectMapper.writeValueAsString(value);
                  } catch (Exception e) {
                    return null;
                  }
                });
    outputStream.to(
        appConf.getPipeline().getSteps().get("testStep").getOutputTopic(),
        Produced.with(Serdes.String(), Serdes.String()));

    return builder.build();
  }

  @Test
  @DisplayName("Test that previously unseen counters will produce zero values")
  void transformCounterInit()
      throws IOException, JSONException, InvalidConfigurations, InvalidMatchConfiguration {
    setup("counter-normalization/application.conf");
    var inputString =
        JsonUtil.readFromInputStream(
            getClass()
                .getClassLoader()
                .getResourceAsStream("counter-normalization/init/input1.json"));
    var expectedString =
        JsonUtil.readFromInputStream(
            getClass()
                .getClassLoader()
                .getResourceAsStream("counter-normalization/init/output1.json"));
    inputTopic.pipeInput("k1", inputString);
    var processed = outputTopic.readValuesToList();
    assertEquals(1, processed.size());
    JSONAssert.assertEquals(expectedString, processed.get(0), true);
  }

  @Test
  @DisplayName(
      "Test counter normalization works under the normal case (counters are normally increasing)")
  void transformCounterNormal()
      throws IOException, JSONException, InvalidConfigurations, InvalidMatchConfiguration {
    setup("counter-normalization/application.conf");
    var input1 =
        JsonUtil.readFromInputStream(
            getClass()
                .getClassLoader()
                .getResourceAsStream("counter-normalization/normal/input1.json"));
    var input2 =
        JsonUtil.readFromInputStream(
            getClass()
                .getClassLoader()
                .getResourceAsStream("counter-normalization/normal/input2.json"));
    var expected1 =
        JsonUtil.readFromInputStream(
            getClass()
                .getClassLoader()
                .getResourceAsStream("counter-normalization/normal/output1.json"));
    var expected2 =
        JsonUtil.readFromInputStream(
            getClass()
                .getClassLoader()
                .getResourceAsStream("counter-normalization/normal/output2.json"));

    inputTopic.pipeInput("k1", input1);
    inputTopic.pipeInput("k1", input2);

    var processed = outputTopic.readValuesToList();
    assertEquals(2, processed.size());
    JSONAssert.assertEquals(expected1, processed.get(0), true);
    JSONAssert.assertEquals(expected2, processed.get(1), true);
  }

  @Test
  void transformCounterNormalMilli()
      throws IOException, JSONException, InvalidConfigurations, InvalidMatchConfiguration {
    setup("counter-normalization/application-milli.conf");
    var input1 =
        JsonUtil.readFromInputStream(
            getClass()
                .getClassLoader()
                .getResourceAsStream("counter-normalization/normal-milli/input1.json"));
    var input2 =
        JsonUtil.readFromInputStream(
            getClass()
                .getClassLoader()
                .getResourceAsStream("counter-normalization/normal-milli/input2.json"));
    var expected1 =
        JsonUtil.readFromInputStream(
            getClass()
                .getClassLoader()
                .getResourceAsStream("counter-normalization/normal-milli/output1.json"));
    var expected2 =
        JsonUtil.readFromInputStream(
            getClass()
                .getClassLoader()
                .getResourceAsStream("counter-normalization/normal-milli/output2.json"));

    inputTopic.pipeInput("k1", input1);
    inputTopic.pipeInput("k1", input2);

    var processed = outputTopic.readValuesToList();
    assertEquals(2, processed.size());
    JSONAssert.assertEquals(expected1, processed.get(0), true);
    JSONAssert.assertEquals(expected2, processed.get(1), true);
  }

  @Test
  void transformCounterNormalSeconds()
      throws IOException, JSONException, InvalidConfigurations, InvalidMatchConfiguration {
    setup("counter-normalization/application-seconds.conf");
    var input1 =
        JsonUtil.readFromInputStream(
            getClass()
                .getClassLoader()
                .getResourceAsStream("counter-normalization/normal-seconds/input1.json"));
    var input2 =
        JsonUtil.readFromInputStream(
            getClass()
                .getClassLoader()
                .getResourceAsStream("counter-normalization/normal-seconds/input2.json"));
    var expected1 =
        JsonUtil.readFromInputStream(
            getClass()
                .getClassLoader()
                .getResourceAsStream("counter-normalization/normal-seconds/output1.json"));
    var expected2 =
        JsonUtil.readFromInputStream(
            getClass()
                .getClassLoader()
                .getResourceAsStream("counter-normalization/normal-seconds/output2.json"));

    inputTopic.pipeInput("k1", input1);
    inputTopic.pipeInput("k1", input2);

    var processed = outputTopic.readValuesToList();
    assertEquals(2, processed.size());
    JSONAssert.assertEquals(expected1, processed.get(0), true);
    JSONAssert.assertEquals(expected2, processed.get(1), true);
  }

  @Test
  @DisplayName("Test caches are correctly reset it's old than the specified MaxCounterCacheAge")
  void transformCounterOldReset()
      throws IOException, JSONException, InvalidConfigurations, InvalidMatchConfiguration {
    setup("counter-normalization/application.conf");
    var inputString =
        JsonUtil.readFromInputStream(
            getClass()
                .getClassLoader()
                .getResourceAsStream("counter-normalization/reset-old-cache/input1.json"));
    var expectedString =
        JsonUtil.readFromInputStream(
            getClass()
                .getClassLoader()
                .getResourceAsStream(
                    "counter-normalization/reset-old-cache/output1-after-cache.json"));
    // Manually save counters for the test
    var stateStore = topologyTestDriver.getTimestampedKeyValueStore(appConf.getStateStoreName());
    // Message is timestamped with "2022-03-14 09:00:00" local time = 1647244800000
    var timestampInCacheRange = MESSAGE_TIMESTAMP - (appConf.getMaxCounterCacheAge() - 1000);
    var timestampOutOfCacheRange = MESSAGE_TIMESTAMP - (appConf.getMaxCounterCacheAge() + 1000);
    stateStore.put(
        Bytes.wrap("hoa01ro1010zoi,TenGigE0/0/0/0,in-pkts".getBytes(StandardCharsets.UTF_8)),
        ValueAndTimestamp.make(
            Bytes.wrap("1".getBytes(StandardCharsets.UTF_8)), timestampInCacheRange));
    stateStore.put(
        Bytes.wrap("hoa01ro1010zoi,TenGigE0/0/0/0,out-pkts".getBytes(StandardCharsets.UTF_8)),
        ValueAndTimestamp.make(
            Bytes.wrap("1".getBytes(StandardCharsets.UTF_8)), timestampOutOfCacheRange));

    inputTopic.pipeInput("k1", inputString);
    var processed = outputTopic.readValuesToList();

    assertEquals(1, processed.size());
    JSONAssert.assertEquals(expectedString, processed.get(0), true);
  }

  @Test
  @DisplayName(
      "Test normalization works correctly after counters wrap around max u32 and u64 is too large to be a normal wrap around")
  void transformCounterUnexpectedReset()
      throws IOException, JSONException, InvalidConfigurations, InvalidMatchConfiguration {
    setup("counter-normalization/application.conf");
    var input1String =
        JsonUtil.readFromInputStream(
            getClass()
                .getClassLoader()
                .getResourceAsStream("counter-normalization/reset/input1.json"));
    var input2String =
        JsonUtil.readFromInputStream(
            getClass()
                .getClassLoader()
                .getResourceAsStream("counter-normalization/reset/input2.json"));
    var expected1String =
        JsonUtil.readFromInputStream(
            getClass()
                .getClassLoader()
                .getResourceAsStream("counter-normalization/reset/output1.json"));
    var expected2String =
        JsonUtil.readFromInputStream(
            getClass()
                .getClassLoader()
                .getResourceAsStream("counter-normalization/reset/output2.json"));

    inputTopic.pipeInput("k1", input1String, 1647244800000L);
    inputTopic.pipeInput("k1", input2String, 1647244860000L);
    var processed = outputTopic.readValuesToList();

    assertEquals(2, processed.size());
    JSONAssert.assertEquals(expected1String, processed.get(0), true);
    JSONAssert.assertEquals(expected2String, processed.get(1), true);
  }

  @Test
  @DisplayName("Test normalization works correctly after counters wrap around max u32 and u64")
  void transformCounterWrap()
      throws IOException, JSONException, InvalidConfigurations, InvalidMatchConfiguration {
    setup("counter-normalization/application.conf");
    var input1String =
        JsonUtil.readFromInputStream(
            getClass()
                .getClassLoader()
                .getResourceAsStream("counter-normalization/wrap/input1.json"));
    var input2String =
        JsonUtil.readFromInputStream(
            getClass()
                .getClassLoader()
                .getResourceAsStream("counter-normalization/wrap/input2.json"));
    var expected1String =
        JsonUtil.readFromInputStream(
            getClass()
                .getClassLoader()
                .getResourceAsStream("counter-normalization/wrap/output1.json"));
    var expected2String =
        JsonUtil.readFromInputStream(
            getClass()
                .getClassLoader()
                .getResourceAsStream("counter-normalization/wrap/output2.json"));

    inputTopic.pipeInput("k1", input1String, 1647244800000L);
    inputTopic.pipeInput("k1", input2String, 1647244860000L);
    var processed = outputTopic.readValuesToList();

    assertEquals(2, processed.size());
    JSONAssert.assertEquals(expected1String, processed.get(0), true);
    JSONAssert.assertEquals(expected2String, processed.get(1), true);
  }

  @Test
  void transformCounterNegative()
      throws IOException, JSONException, InvalidConfigurations, InvalidMatchConfiguration {
    setup("counter-normalization/application.conf");
    var input1String =
        JsonUtil.readFromInputStream(
            getClass()
                .getClassLoader()
                .getResourceAsStream("counter-normalization/negative/input1.json"));
    var input2String =
        JsonUtil.readFromInputStream(
            getClass()
                .getClassLoader()
                .getResourceAsStream("counter-normalization/negative/input2.json"));
    var input3String =
        JsonUtil.readFromInputStream(
            getClass()
                .getClassLoader()
                .getResourceAsStream("counter-normalization/negative/input3.json"));
    var expected1String =
        JsonUtil.readFromInputStream(
            getClass()
                .getClassLoader()
                .getResourceAsStream("counter-normalization/wrap/output1.json"));
    var expected2String =
        JsonUtil.readFromInputStream(
            getClass()
                .getClassLoader()
                .getResourceAsStream("counter-normalization/negative/output2.json"));
    var expected3String =
        JsonUtil.readFromInputStream(
            getClass()
                .getClassLoader()
                .getResourceAsStream("counter-normalization/negative/output3.json"));

    inputTopic.pipeInput("k1", input1String);
    inputTopic.pipeInput("k1", input2String);
    inputTopic.pipeInput("k1", input3String);
    var processed = outputTopic.readValuesToList();

    assertEquals(3, processed.size());
    JSONAssert.assertEquals(expected1String, processed.get(0), true);
    JSONAssert.assertEquals(expected2String, processed.get(1), true);
    JSONAssert.assertEquals(expected3String, processed.get(2), true);
  }

  @Test
  void transformCounterOlderMilli()
      throws IOException, JSONException, InvalidConfigurations, InvalidMatchConfiguration {
    setup("counter-normalization/application-milli.conf");
    var input1 =
        JsonUtil.readFromInputStream(
            getClass()
                .getClassLoader()
                .getResourceAsStream("counter-normalization/older/input1.json"));
    var input2 =
        JsonUtil.readFromInputStream(
            getClass()
                .getClassLoader()
                .getResourceAsStream("counter-normalization/older/input2.json"));
    var expected1 =
        JsonUtil.readFromInputStream(
            getClass()
                .getClassLoader()
                .getResourceAsStream("counter-normalization/older/output1.json"));
    var expected2 =
        JsonUtil.readFromInputStream(
            getClass()
                .getClassLoader()
                .getResourceAsStream("counter-normalization/older/output2.json"));

    inputTopic.pipeInput("k1", input1);
    inputTopic.pipeInput("k1", input2);

    var processed = outputTopic.readValuesToList();
    assertEquals(2, processed.size());
    JSONAssert.assertEquals(expected1, processed.get(0), true);
    JSONAssert.assertEquals(expected2, processed.get(1), true);
  }
}
