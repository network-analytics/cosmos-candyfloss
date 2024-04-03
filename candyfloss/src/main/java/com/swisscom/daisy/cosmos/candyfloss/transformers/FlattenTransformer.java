package com.swisscom.daisy.cosmos.candyfloss.transformers;

import com.swisscom.daisy.cosmos.candyfloss.messages.ErrorMessage;
import com.swisscom.daisy.cosmos.candyfloss.messages.FlattenedMessage;
import com.swisscom.daisy.cosmos.candyfloss.messages.ValueErrorMessage;
import com.swisscom.daisy.cosmos.candyfloss.transformations.TransformedMessage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.processor.ProcessorContext;

public class FlattenTransformer
    implements Transformer<
        String,
        TransformedMessage,
        Iterable<KeyValue<String, ValueErrorMessage<FlattenedMessage>>>> {
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

  private ProcessorContext context;

  @Override
  public void init(ProcessorContext context) {
    this.context = context;
  }

  @Override
  public void close() {}

  @Override
  public Iterable<KeyValue<String, ValueErrorMessage<FlattenedMessage>>> transform(
      String key, TransformedMessage value) {
    try {
      counterIn.increment();
      return flattenMessage(key, value);
    } catch (Exception e) {
      counterError.increment();
      var error = ErrorMessage.getError(context, getClass().getName(), key, value, e.getMessage());
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
