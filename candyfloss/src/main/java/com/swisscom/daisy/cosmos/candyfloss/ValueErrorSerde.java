package com.swisscom.daisy.cosmos.candyfloss;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swisscom.daisy.cosmos.candyfloss.messages.ValueErrorMessage;
import io.vavr.NotImplementedError;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

public class ValueErrorSerde<T> implements Serde<ValueErrorMessage<T>> {
  @Override
  public Serializer<ValueErrorMessage<T>> serializer() {
    return new CustomSerializer<>();
  }

  @Override
  public Deserializer<ValueErrorMessage<T>> deserializer() {
    throw new NotImplementedError();
  }

  static class CustomSerializer<R> implements Serializer<ValueErrorMessage<R>> {
    private final ObjectMapper objectMapper =
        new ObjectMapper().configure(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS, true);

    @Override
    public byte[] serialize(String topic, ValueErrorMessage<R> data) {
      try {
        return objectMapper
            .writeValueAsString(data.getErrorMessage())
            .getBytes(StandardCharsets.UTF_8);
      } catch (JsonProcessingException ex) {
        return ex.getMessage().getBytes(StandardCharsets.UTF_8);
      }
    }
  }
}
