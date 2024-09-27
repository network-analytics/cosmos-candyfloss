package com.swisscom.daisy.cosmos.candyfloss.transformers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.swisscom.daisy.cosmos.candyfloss.messages.ErrorMessage;
import com.swisscom.daisy.cosmos.candyfloss.messages.ValueErrorMessage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;
import java.util.Map;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.processor.ProcessorContext;

public class FromJsonTransformer
    implements Transformer<String, String, KeyValue<String, ValueErrorMessage<DocumentContext>>> {
  private static final Configuration configuration =
      Configuration.builder().options(Option.SUPPRESS_EXCEPTIONS).build();
  private final Counter counterIn =
      Counter.builder("json_streams_deserialize_json_in")
          .description("Number of message incoming to the json deserialization step")
          .register(Metrics.globalRegistry);
  private final Counter counterOut =
      Counter.builder("json_streams_deserialize_json_out")
          .description("Number of output messages after the deserialization step")
          .register(Metrics.globalRegistry);
  private final Counter counterError =
      Counter.builder("json_streams_deserialize_json_error")
          .description("Number of error messages that are discarded to dlq topic")
          .register(Metrics.globalRegistry);

  private final ObjectMapper objectMapper =
      new ObjectMapper().configure(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS, true);
  private ProcessorContext context;

  @Override
  public void init(ProcessorContext context) {
    this.context = context;
  }

  @Override
  public KeyValue<String, ValueErrorMessage<DocumentContext>> transform(String key, String value) {
    try {
      counterIn.increment();
      return process(key, value);
    } catch (Exception e) {
      counterError.increment();
      var error = ErrorMessage.getError(context, getClass().getName(), key, value, e.getMessage());
      return KeyValue.pair(key, new ValueErrorMessage<>(null, error));
    }
  }

  @SuppressWarnings("unchecked")
  private KeyValue<String, ValueErrorMessage<DocumentContext>> process(String key, String value)
      throws JsonProcessingException {
    Map<String, Object> jsonMap = objectMapper.readValue(value, Map.class);
    DocumentContext context = JsonPath.using(configuration).parse(jsonMap);
    counterOut.increment();
    return KeyValue.pair(key, new ValueErrorMessage<>(context, null));
  }

  @Override
  public void close() {}
}
