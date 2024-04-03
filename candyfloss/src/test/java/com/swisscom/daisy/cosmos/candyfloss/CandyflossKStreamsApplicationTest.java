package com.swisscom.daisy.cosmos.candyfloss;

import static org.junit.jupiter.api.Assertions.*;

import com.swisscom.daisy.cosmos.candyfloss.config.JsonKStreamApplicationConfig;
import com.swisscom.daisy.cosmos.candyfloss.config.exceptions.InvalidConfigurations;
import com.swisscom.daisy.cosmos.candyfloss.testutils.JsonUtil;
import com.swisscom.daisy.cosmos.candyfloss.transformations.match.exceptions.InvalidMatchConfiguration;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.vavr.Tuple;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.ValueAndTimestamp;
import org.json.JSONException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CandyflossKStreamsApplicationTest {
  private TopologyTestDriver topologyTestDriver;
  private JsonKStreamApplicationConfig appConf;
  private TestInputTopic<String, String> inputTopic;
  private Map<String, TestOutputTopic<String, String>> outputTopics;

  @AfterEach
  public void cleanUp() {
    topologyTestDriver.close();
  }

  private void setupTopology(String applicationConfPath)
      throws IOException, InvalidConfigurations, InvalidMatchConfiguration {
    Config config = ConfigFactory.load(applicationConfPath);
    appConf = JsonKStreamApplicationConfig.fromConfig(config);
    CandyflossKStreamsApplication app = new CandyflossKStreamsApplication(appConf);
    final Topology topology = app.buildTopology();
    topologyTestDriver = new TopologyTestDriver(topology, appConf.getKafkaProperties());
    var stringSerde = Serdes.String();
    inputTopic =
        topologyTestDriver.createInputTopic(
            appConf.getInputTopicName(), stringSerde.serializer(), stringSerde.serializer());
    outputTopics =
        appConf.getPipeline().getSteps().values().stream()
            .map(
                x ->
                    Tuple.of(
                        x.getOutputTopic(),
                        topologyTestDriver.createOutputTopic(
                            x.getOutputTopic(),
                            stringSerde.deserializer(),
                            stringSerde.deserializer())))
            .collect(Collectors.toMap(k -> k._1(), v -> v._2()));
    outputTopics.put(
        appConf.getDlqTopicName(),
        topologyTestDriver.createOutputTopic(
            appConf.getDlqTopicName(), stringSerde.deserializer(), stringSerde.deserializer()));
    outputTopics.put(
        appConf.getDiscardTopicName(),
        topologyTestDriver.createOutputTopic(
            appConf.getDiscardTopicName(), stringSerde.deserializer(), stringSerde.deserializer()));
  }

  @Test
  public void testApplication()
      throws IOException, InvalidConfigurations, InvalidMatchConfiguration {
    setupTopology("transformers/application.conf");
    var ciscoOutputTopic =
        outputTopics.get(appConf.getPipeline().getSteps().get("output1").getOutputTopic());
    var openconfigInterfaceOutputTopic =
        outputTopics.get(appConf.getPipeline().getSteps().get("output2").getOutputTopic());
    var dumpInitOutputTopic =
        outputTopics.get(appConf.getPipeline().getSteps().get("output3").getOutputTopic());

    inputTopic.pipeInput(
        "hoa01ro1010zoi",
        JsonUtil.readFromInputStream(
            getClass()
                .getClassLoader()
                .getResourceAsStream("transformers/telemetry-decoded.json")));
    inputTopic.pipeInput(
        "hoa01ro1010zoi",
        JsonUtil.readFromInputStream(
            getClass().getClassLoader().getResourceAsStream("transformers/dump-init-input.json")));
    inputTopic.pipeInput("aa", "aa");

    assertTrue(ciscoOutputTopic.isEmpty());
    assertFalse(openconfigInterfaceOutputTopic.isEmpty());
    assertFalse(dumpInitOutputTopic.isEmpty());
  }

  @Test
  public void testApplicationWithPreTransform()
      throws IOException, InvalidConfigurations, InvalidMatchConfiguration {
    setupTopology("transformers/application-pre-transform.conf");
    var ciscoOutputTopic =
        outputTopics.get(appConf.getPipeline().getSteps().get("output1").getOutputTopic());
    var openconfigInterfaceOutputTopic =
        outputTopics.get(appConf.getPipeline().getSteps().get("output2").getOutputTopic());
    var dumpInitOutputTopic =
        outputTopics.get(appConf.getPipeline().getSteps().get("output3").getOutputTopic());

    inputTopic.pipeInput(
        "hoa01ro1010zoi",
        JsonUtil.readFromInputStream(
            getClass()
                .getClassLoader()
                .getResourceAsStream("transformers/telemetry-decoded.json")));
    inputTopic.pipeInput(
        "hoa01ro1010zoi",
        JsonUtil.readFromInputStream(
            getClass().getClassLoader().getResourceAsStream("transformers/dump-init-input.json")));
    inputTopic.pipeInput("aa", "aa");

    assertTrue(ciscoOutputTopic.isEmpty());
    assertFalse(openconfigInterfaceOutputTopic.isEmpty());
    assertFalse(dumpInitOutputTopic.isEmpty());
  }

  @Test
  @DisplayName("Test caches are correctly scanned and cleared from old unused values")
  void transformCounterOldCountersCacheClear()
      throws IOException, JSONException, InvalidConfigurations, InvalidMatchConfiguration {
    setupTopology("transformers/application.conf");
    // Manually save counters for the test
    KeyValueStore<Bytes, ValueAndTimestamp<Bytes>> stateStore =
        topologyTestDriver.getTimestampedKeyValueStore(appConf.getStateStoreName());
    var key1 = Bytes.wrap("hoa01ro1010zoi,TenGigE0/0/0/0,in-pkts".getBytes(StandardCharsets.UTF_8));
    var key2 =
        Bytes.wrap("hoa01ro1010zoi,TenGigE0/0/0/0,out-pkts".getBytes(StandardCharsets.UTF_8));
    // Message is timestamped with "2022-03-14 09:00:00" local time = 1647244800000
    var timestampInCacheRange = 1647244800000L - (appConf.getMaxCounterCacheAge() - 1000);
    var timestampOutOfCacheRange = 1647244800000L - (appConf.getMaxCounterCacheAge() + 1000);
    stateStore.put(
        key1,
        ValueAndTimestamp.make(
            Bytes.wrap("1".getBytes(StandardCharsets.UTF_8)), timestampInCacheRange));
    stateStore.put(
        key2,
        ValueAndTimestamp.make(
            Bytes.wrap("1".getBytes(StandardCharsets.UTF_8)), timestampOutOfCacheRange));

    // We put a message with a timestamp in the future, so the old counter clear task is triggered
    inputTopic.pipeInput("k1", "arbitrary string", timestampInCacheRange + 10);

    // We check only one key remains
    var iter = stateStore.all();
    List<Bytes> storedKeys = new ArrayList<>();
    while (iter.hasNext()) {
      storedKeys.add(iter.next().key);
    }
    assertEquals(1, storedKeys.size());
    assertEquals(key1, storedKeys.get(0));
  }
}
