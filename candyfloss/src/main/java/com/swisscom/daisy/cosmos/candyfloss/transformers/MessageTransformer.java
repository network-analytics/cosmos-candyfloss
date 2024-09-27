package com.swisscom.daisy.cosmos.candyfloss.transformers;

import com.jayway.jsonpath.DocumentContext;
import com.swisscom.daisy.cosmos.candyfloss.config.PipelineConfig;
import com.swisscom.daisy.cosmos.candyfloss.messages.ErrorMessage;
import com.swisscom.daisy.cosmos.candyfloss.messages.ValueErrorMessage;
import com.swisscom.daisy.cosmos.candyfloss.transformations.TransformedMessage;
import com.swisscom.daisy.cosmos.candyfloss.transformations.Transformer;
import com.swisscom.daisy.cosmos.candyfloss.transformations.match.Match;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.processor.ProcessorContext;

public class MessageTransformer
    implements org.apache.kafka.streams.kstream.Transformer<
        String, DocumentContext, KeyValue<String, ValueErrorMessage<TransformedMessage>>> {
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
      String key, DocumentContext value) {
    try {
      counterMsg.increment();

      List<KeyValue<String, ValueErrorMessage<TransformedMessage>>> pairs = process(key, value);

      for (KeyValue<String, ValueErrorMessage<TransformedMessage>> kv : pairs) {
        context.forward(kv.key, kv.value);
      }
      return null;

    } catch (Exception e) {
      counterError.increment();
      var error =
          ErrorMessage.getError(context, getClass().getName(), key, value.json(), e.getMessage());
      return KeyValue.pair(key, new ValueErrorMessage<>(null, error));
    }
  }

  private List<KeyValue<String, ValueErrorMessage<TransformedMessage>>> process(
      String key, DocumentContext context) {
    var transformed =
        matchTransformPairs.stream()
            .parallel()
            .filter(x -> x.getMatch().matchContext(context))
            .map(
                x ->
                    new TransformedMessage(
                        x.getTransformer().transformList(context), x.getMatch().getTag()))
            .map(x -> KeyValue.pair(key, new ValueErrorMessage<>(x)));

    var result =
        Optional.of(
                transformed.map(x -> KeyValue.pair(x.key, x.value)).collect(Collectors.toList()))
            .filter(l -> !l.isEmpty())
            .orElse(
                List.of(
                    KeyValue.pair(
                        key, new ValueErrorMessage<>(new TransformedMessage(List.of(context))))));

    return result;
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
