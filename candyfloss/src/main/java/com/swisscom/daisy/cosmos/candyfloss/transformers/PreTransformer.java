package com.swisscom.daisy.cosmos.candyfloss.transformers;

import com.swisscom.daisy.cosmos.candyfloss.messages.ErrorMessage;
import com.swisscom.daisy.cosmos.candyfloss.messages.ValueErrorMessage;
import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;
import java.util.Map;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.processor.ProcessorContext;

public class PreTransformer
    implements Transformer<
        String, Map<String, Object>, KeyValue<String, ValueErrorMessage<Map<String, Object>>>> {
  private final Counter counterIn =
      Counter.builder("json_streams_pre_transformer_in")
          .description("Number of message incoming to the Json Pre-Transformer step")
          .register(Metrics.globalRegistry);

  private final Counter counterError =
      Counter.builder("json_streams_pre_transformer_error")
          .description("Number of error messages that are discarded to dlq topic")
          .register(Metrics.globalRegistry);

  private ProcessorContext context;

  private final com.swisscom.daisy.cosmos.candyfloss.transformations.Transformer transformer;

  public PreTransformer(
      @Nullable com.swisscom.daisy.cosmos.candyfloss.transformations.Transformer transformer) {
    this.transformer = transformer;
  }

  @Override
  public void init(ProcessorContext context) {
    this.context = context;
  }

  @Override
  public void close() {}

  @Override
  public KeyValue<String, ValueErrorMessage<Map<String, Object>>> transform(
      String key, Map<String, Object> value) {
    try {
      counterIn.increment();
      if (transformer == null) {
        return KeyValue.pair(key, new ValueErrorMessage<>(value));
      } else {
        var transformed = this.transformer.transform(value);
        return KeyValue.pair(key, new ValueErrorMessage<>(transformed));
      }
    } catch (Exception e) {
      counterError.increment();
      var error = ErrorMessage.getError(context, getClass().getName(), key, value, e.getMessage());
      return KeyValue.pair(key, new ValueErrorMessage<>(null, error));
    }
  }
}
