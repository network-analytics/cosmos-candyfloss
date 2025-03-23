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
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Timer.Sample;
import java.util.Map;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;

public class FromJsonProcessor
    implements Processor<String, String, String, ValueErrorMessage<DocumentContext>> {
  private static final Configuration configuration =
      Configuration.builder().options(Option.SUPPRESS_EXCEPTIONS).build();
  private static final String metricTag = "partition";
  private static final String timerMetric = "latency_deserialization";
  private static final String timerErrorMetric = "latency_deserialization_error";
  private final Counter counterIn =
      Counter.builder("json_streams_deserialization_in")
          .description("Number of message incoming to the json deserialization step")
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
    return KeyValue.pair(key, new ValueErrorMessage<>(context, null));
  }

  @Override
  public void process(Record<String, String> record) {
    counterIn.increment();

    Sample timer = Timer.start(Metrics.globalRegistry);

    String key = record.key();
    String value = record.value();
    long ts = record.timestamp();

    var partition =
        context.recordMetadata().isPresent()
            ? String.valueOf(context.recordMetadata().get().partition())
            : "";

    try {
      KeyValue<String, ValueErrorMessage<DocumentContext>> kv = handleRecord(key, value);

      context.forward(new Record<>(kv.key, kv.value, record.timestamp()));

      timer.stop(Metrics.globalRegistry.timer(timerMetric, metricTag, partition));
    } catch (Exception e) {
      var error = new ErrorMessage(context, getClass().getName(), key, value, ts, e.getMessage());
      context.forward(new Record<>(key, new ValueErrorMessage<>(null, error), record.timestamp()));

      timer.stop(Metrics.globalRegistry.timer(timerErrorMetric, metricTag, partition));
    }
  }
}
