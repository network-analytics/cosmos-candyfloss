package com.swisscom.daisy.cosmos.candyfloss.processors;

import com.jayway.jsonpath.DocumentContext;
import com.swisscom.daisy.cosmos.candyfloss.messages.ErrorMessage;
import com.swisscom.daisy.cosmos.candyfloss.messages.ValueErrorMessage;
import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;

public class PreProcessor
    implements Processor<String, DocumentContext, String, ValueErrorMessage<DocumentContext>> {
  private final Counter counterIn =
      Counter.builder("json_streams_pre_transformer_in")
          .description("Number of message incoming to the Json Pre-Transformer step")
          .register(Metrics.globalRegistry);

  private final Counter counterError =
      Counter.builder("json_streams_pre_transformer_error")
          .description("Number of error messages that are discarded to dlq topic")
          .register(Metrics.globalRegistry);

  private ProcessorContext<String, ValueErrorMessage<DocumentContext>> context;

  private final com.swisscom.daisy.cosmos.candyfloss.transformations.Transformer transformer;

  public PreProcessor(
      @Nullable com.swisscom.daisy.cosmos.candyfloss.transformations.Transformer transformer) {
    this.transformer = transformer;
  }

  @Override
  public void init(ProcessorContext<String, ValueErrorMessage<DocumentContext>> context) {
    this.context = context;
  }

  @Override
  public void process(Record<String, DocumentContext> record) {
    var key = record.key();
    var value = record.value();
    var ts = record.timestamp();
    var kv = handleRecord(key, value, ts);
    context.forward(new Record<>(kv.key, kv.value, ts));
  }

  public KeyValue<String, ValueErrorMessage<DocumentContext>> handleRecord(
      String key, DocumentContext value, long ts) {
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
      var error = new ErrorMessage(context, getClass().getName(), key, value, ts, e.getMessage());
      return KeyValue.pair(key, new ValueErrorMessage<>(null, error));
    }
  }
}
