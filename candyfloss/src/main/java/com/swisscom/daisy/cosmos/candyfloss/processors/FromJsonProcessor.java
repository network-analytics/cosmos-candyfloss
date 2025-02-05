package com.swisscom.daisy.cosmos.candyfloss.processors;

import static com.swisscom.daisy.cosmos.candyfloss.transformations.jolt.CustomFunctions.factory;

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
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;

public class FromJsonProcessor
    implements Processor<String, String, String, ValueErrorMessage<DocumentContext>> {
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
      new ObjectMapper(factory).configure(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS, true);
  private ProcessorContext<String, ValueErrorMessage<DocumentContext>> context;

  @Override
  public void init(ProcessorContext<String, ValueErrorMessage<DocumentContext>> context) {
    this.context = context;
  }

  private KeyValue<String, ValueErrorMessage<DocumentContext>> handleRecord(
      String key, String value) throws JsonProcessingException {
    @SuppressWarnings("unchecked")
    Map<String, Object> jsonMap = objectMapper.readValue(value, Map.class);
    DocumentContext context = JsonPath.using(configuration).parse(jsonMap);
    counterOut.increment();
    return KeyValue.pair(key, new ValueErrorMessage<>(context, null));
  }

  @Override
  public void process(Record<String, String> record) {
    String key = record.key();
    String value = record.value();
    long ts = record.timestamp();

    try {
      counterIn.increment();
      KeyValue<String, ValueErrorMessage<DocumentContext>> kv = handleRecord(key, value);

      context.forward(new Record<>(kv.key, kv.value, record.timestamp()));
    } catch (Exception e) {
      counterError.increment();
      var error = new ErrorMessage(context, getClass().getName(), key, value, ts, e.getMessage());
      context.forward(new Record<>(key, new ValueErrorMessage<>(null, error), record.timestamp()));
    }
  }
}
