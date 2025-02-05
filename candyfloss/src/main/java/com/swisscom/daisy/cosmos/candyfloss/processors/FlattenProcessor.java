package com.swisscom.daisy.cosmos.candyfloss.processors;

import com.swisscom.daisy.cosmos.candyfloss.messages.ErrorMessage;
import com.swisscom.daisy.cosmos.candyfloss.messages.FlattenedMessage;
import com.swisscom.daisy.cosmos.candyfloss.messages.ValueErrorMessage;
import com.swisscom.daisy.cosmos.candyfloss.transformations.TransformedMessage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;

public class FlattenProcessor
    implements Processor<String, TransformedMessage, String, ValueErrorMessage<FlattenedMessage>> {
  private final Counter counterIn =
      Counter.builder("json_streams_flatten_in")
          .description("Number of message incoming to the Json Flatten step")
          .register(Metrics.globalRegistry);
  private final Counter counterOut =
      Counter.builder("json_streams_flatten_out")
          .description(
              "Number of output messages after the flatten step. "
                  + "Note: json_streams_flatten_out/(json_streams_flatten_in-json_streams_flatten_error)"
                  + " is flatten message ration")
          .register(Metrics.globalRegistry);
  private final Counter counterError =
      Counter.builder("json_streams_flatten_error")
          .description("Number of error messages that are discarded to dlq topic")
          .register(Metrics.globalRegistry);

  private ProcessorContext<String, ValueErrorMessage<FlattenedMessage>> context;

  @Override
  public void init(ProcessorContext<String, ValueErrorMessage<FlattenedMessage>> context) {
    this.context = context;
  }

  @Override
  public void process(Record<String, TransformedMessage> record) {
    var key = record.key();
    var value = record.value();
    var ts = record.timestamp();

    handleRecord(key, value, ts).forEach(kv -> context.forward(new Record<>(kv.key, kv.value, ts)));
  }

  public Iterable<KeyValue<String, ValueErrorMessage<FlattenedMessage>>> handleRecord(
      String key, TransformedMessage value, long ts) {
    try {
      counterIn.increment();
      return flattenMessage(key, value);
    } catch (Exception e) {
      counterError.increment();
      var error = new ErrorMessage(context, getClass().getName(), key, value, ts, e.getMessage());
      return List.of(KeyValue.pair(key, new ValueErrorMessage<>(null, error)));
    }
  }

  private List<KeyValue<String, ValueErrorMessage<FlattenedMessage>>> flattenMessage(
      String key, TransformedMessage value) {
    if (value.getValue() == null) {
      return List.of();
    }
    return value.getValue().stream()
        .map(
            x ->
                KeyValue.pair(
                    key, new ValueErrorMessage<>(new FlattenedMessage(x, value.getTag()))))
        .peek(x -> counterOut.increment())
        .collect(Collectors.toList());
  }
}
