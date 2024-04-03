package com.swisscom.daisy.cosmos.candyfloss.transformers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.protobuf.InvalidProtocolBufferException;
import com.swisscom.daisy.cosmos.candyfloss.messages.ErrorMessage;
import com.swisscom.daisy.cosmos.candyfloss.messages.ValueErrorMessage;
import com.swisscom.daisy.cosoms.candyfloss.protobufdecoder.ProtoBufDecoder;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import java.util.Map;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.processor.ProcessorContext;

@Deprecated
public class ProtoBufDecodingTransformer
    implements Transformer<
        String, Map<String, Object>, KeyValue<String, ValueErrorMessage<Map<String, Object>>>> {
  private final Counter counterError =
      Counter.builder("json_streams_protobuf_decode_error")
          .description("Number of error messages that are discarded to dlq topic")
          .register(Metrics.globalRegistry);

  private final Timer timer =
      Timer.builder("json_streams_protobuf_decode_duration")
          .description("Time spent in decoding protobuf from the message")
          .publishPercentileHistogram()
          .register(Metrics.globalRegistry);

  private final ProtoBufDecoder protoBufDecoder = new ProtoBufDecoder();
  private ProcessorContext context;

  @Override
  public void init(ProcessorContext context) {
    this.context = context;
  }

  @Override
  public KeyValue<String, ValueErrorMessage<Map<String, Object>>> transform(
      String key, Map<String, Object> value) {
    return timer.record(
        () -> {
          try {
            return process(key, value);
          } catch (Exception e) {
            counterError.increment();
            var error =
                ErrorMessage.getError(context, getClass().getName(), key, value, e.getMessage());
            return KeyValue.pair(key, new ValueErrorMessage<>(null, error));
          }
        });
  }

  private KeyValue<String, ValueErrorMessage<Map<String, Object>>> process(
      String key, Map<String, Object> value)
      throws InvalidProtocolBufferException, JsonProcessingException {
    var decoded = protoBufDecoder.decode(value);
    return KeyValue.pair(key, new ValueErrorMessage<>(decoded, null));
  }

  @Override
  public void close() {}
}
