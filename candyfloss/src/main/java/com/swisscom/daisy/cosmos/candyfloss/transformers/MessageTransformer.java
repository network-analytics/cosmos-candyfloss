package com.swisscom.daisy.cosmos.candyfloss.transformers;

import com.swisscom.daisy.cosmos.candyfloss.config.PipelineConfig;
import com.swisscom.daisy.cosmos.candyfloss.messages.ErrorMessage;
import com.swisscom.daisy.cosmos.candyfloss.messages.ValueErrorMessage;
import com.swisscom.daisy.cosmos.candyfloss.transformations.TransformedMessage;
import com.swisscom.daisy.cosmos.candyfloss.transformations.Transformer;
import com.swisscom.daisy.cosmos.candyfloss.transformations.match.Match;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.processor.ProcessorContext;

public class MessageTransformer
    implements org.apache.kafka.streams.kstream.Transformer<
        String, Map<String, Object>, KeyValue<String, ValueErrorMessage<TransformedMessage>>> {
  private final Counter counterMsg =
      Counter.builder("json_streams_transformer_in")
          .description("Number of message incoming to the MessageTransformer step")
          .register(Metrics.globalRegistry);
  private final Counter counterError =
      Counter.builder("json_streams_transformer_error")
          .description("Number of error messages that are discarded to dlq topic")
          .register(Metrics.globalRegistry);

  private final PipelineConfig pipelineConfig;
  private final List<MatchTransformPair> matchTransformPairs;
  private ProcessorContext context;

  public MessageTransformer(PipelineConfig pipelineConfig) {
    this.pipelineConfig = pipelineConfig;
    this.matchTransformPairs =
        this.pipelineConfig.getSteps().values().stream()
            .map(
                x ->
                    new MessageTransformer.MatchTransformPair(
                        x.getMatch(), new Transformer(x.getTransform())))
            .collect(Collectors.toList());
  }

  @Override
  public void init(ProcessorContext context) {
    this.context = context;
  }

  @Override
  public KeyValue<String, ValueErrorMessage<TransformedMessage>> transform(
      String key, Map<String, Object> value) {
    try {
      counterMsg.increment();
      return process(key, value);
    } catch (Exception e) {
      counterError.increment();
      var error = ErrorMessage.getError(context, getClass().getName(), key, value, e.getMessage());
      return KeyValue.pair(key, new ValueErrorMessage<>(null, error));
    }
  }

  private KeyValue<String, ValueErrorMessage<TransformedMessage>> process(
      String key, Map<String, Object> value) {
    var transformed =
        matchTransformPairs.stream()
            .filter(x -> x.getMatch().match(value))
            .map(
                x ->
                    new TransformedMessage(
                        x.getTransformer().transformList(value), x.getMatch().getTag()))
            .map(x -> KeyValue.pair(key, new ValueErrorMessage<>(x)))
            .findFirst();
    return transformed.orElseGet(
        () -> KeyValue.pair(key, new ValueErrorMessage<>(new TransformedMessage(List.of(value)))));
  }

  @Override
  public void close() {}

  @Getter
  @AllArgsConstructor
  private static class MatchTransformPair {
    private Match match;
    private Transformer transformer;
  }
}
