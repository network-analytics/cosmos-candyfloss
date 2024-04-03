package com.swisscom.daisy.cosmos.candyfloss;

import static org.junit.jupiter.api.Assertions.*;

import com.swisscom.daisy.cosmos.candyfloss.config.JsonKStreamApplicationConfig;
import com.swisscom.daisy.cosmos.candyfloss.config.exceptions.InvalidConfigurations;
import com.swisscom.daisy.cosmos.candyfloss.transformations.match.exceptions.InvalidMatchConfiguration;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.*;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class AvroInputTest {
  private TopologyTestDriver topologyTestDriver;
  private JsonKStreamApplicationConfig appConf;
  private TestInputTopic<String, GenericRecord> inputTopic;
  private Map<String, TestOutputTopic<String, String>> outputTopics;

  private SchemaRegistryClient schemaRegistryClient;

  @AfterEach
  public void cleanUp() {
    topologyTestDriver.close();
  }

  @SuppressWarnings("unchecked")
  private <T> Serializer<T> getSerializer(boolean isKey, String schemaUrl) {
    Map<String, Object> map = new HashMap<>();
    map.put(KafkaAvroDeserializerConfig.AUTO_REGISTER_SCHEMAS, true);
    map.put(KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaUrl);
    Serializer<T> serializer = (Serializer<T>) new KafkaAvroSerializer(schemaRegistryClient);
    serializer.configure(map, isKey);
    return serializer;
  }

  @SuppressWarnings("unchecked")
  private <T> Deserializer<T> getDeserializer(boolean key, String schemaUrl) {
    Map<String, Object> map = new HashMap<>();
    map.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, "false");
    map.put(KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaUrl);
    Deserializer<T> deserializer =
        (Deserializer<T>) new KafkaAvroDeserializer(schemaRegistryClient);
    deserializer.configure(map, key);
    return deserializer;
  }

  private void setupTopology(String applicationConfPath)
      throws IOException, InvalidConfigurations, InvalidMatchConfiguration {
    Config config = ConfigFactory.load(applicationConfPath);
    appConf = JsonKStreamApplicationConfig.fromConfig(config);
    var schemaUrl =
        appConf
            .getKafkaProperties()
            .getProperty(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG);
    var scope = MockSchemaRegistry.validateAndMaybeGetMockScope(List.of(schemaUrl));
    schemaRegistryClient = MockSchemaRegistry.getClientForScope(scope);
    CandyflossKStreamsApplication app = new CandyflossKStreamsApplication(appConf);
    final Topology topology = app.buildTopology();
    topologyTestDriver = new TopologyTestDriver(topology, appConf.getKafkaProperties());
    var stringSerde = Serdes.String();
    Serializer<GenericRecord> avroSerializer = getSerializer(false, schemaUrl);
    inputTopic =
        topologyTestDriver.createInputTopic(
            appConf.getInputTopicName(), stringSerde.serializer(), avroSerializer);
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
            .collect(Collectors.toMap(Tuple2::_1, Tuple2::_2));
    outputTopics.put(
        appConf.getDlqTopicName(),
        topologyTestDriver.createOutputTopic(
            appConf.getDlqTopicName(), stringSerde.deserializer(), stringSerde.deserializer()));
    outputTopics.put(
        appConf.getDiscardTopicName(),
        topologyTestDriver.createOutputTopic(
            appConf.getDiscardTopicName(), stringSerde.deserializer(), stringSerde.deserializer()));
  }

  public GenericRecord genDeserializeFromJson(String message, Schema schema) throws IOException {
    DatumReader<GenericRecord> reader = new GenericDatumReader<>(schema);
    Decoder decoder = DecoderFactory.get().jsonDecoder(schema, message);
    return reader.read(null, decoder);
  }

  @Test
  public void testApplication()
      throws IOException, InvalidConfigurations, InvalidMatchConfiguration, RestClientException {
    setupTopology("avro-input/application.conf");
    var outputTopic =
        outputTopics.get(appConf.getPipeline().getSteps().get("output1").getOutputTopic());

    // Register schema
    var parser = new Schema.Parser();
    var schema =
        parser.parse(
            getClass().getClassLoader().getResourceAsStream("avro-input/test-schema.avsc"));
    schemaRegistryClient.register(appConf.getInputTopicName() + "-value", schema);

    GenericRecord avroInputMsg = genDeserializeFromJson("{\"event\":\"value\"}", schema);

    inputTopic.pipeInput(avroInputMsg);
    assertEquals(List.of("{\"transformed_event\":\"value\"}"), outputTopic.readValuesToList());
  }
}
