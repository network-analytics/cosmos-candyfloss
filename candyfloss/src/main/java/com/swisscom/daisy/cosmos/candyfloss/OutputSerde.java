package com.swisscom.daisy.cosmos.candyfloss;

import com.swisscom.daisy.cosmos.candyfloss.messages.OutputMessage;
import io.vavr.NotImplementedError;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

public class OutputSerde implements Serde<OutputMessage> {
  @Override
  public Serializer<OutputMessage> serializer() {
    return new CustomSerializer();
  }

  @Override
  public Deserializer<OutputMessage> deserializer() {
    throw new NotImplementedError();
  }

  static class CustomSerializer implements Serializer<OutputMessage> {
    @Override
    public byte[] serialize(String topic, OutputMessage data) {
      return data.getValue().getBytes(StandardCharsets.UTF_8);
    }
  }
}
