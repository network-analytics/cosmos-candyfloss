package com.swisscom.daisy.cosmos.candyfloss.processors;

import com.jayway.jsonpath.DocumentContext;
import com.swisscom.daisy.cosmos.candyfloss.config.PipelineConfig;
import com.swisscom.daisy.cosmos.candyfloss.messages.ErrorMessage;
import com.swisscom.daisy.cosmos.candyfloss.messages.ValueErrorMessage;
import com.swisscom.daisy.cosmos.candyfloss.transformations.TransformedMessage;
import com.swisscom.daisy.cosmos.candyfloss.transformations.Transformer;
import com.swisscom.daisy.cosmos.candyfloss.transformations.match.Match;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Timer.Sample;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;

public class MessageProcessor
        implements Processor<String, DocumentContext, String, ValueErrorMessage<TransformedMessage>> {
  private static final String metricTag = "pipeline";
  private static final String timerMetric = "latency_transform";
  private static final String timerErrorMetric = "latency_transform_error";
  private final Counter counterIn =
          Counter.builder("json_streams_transformer_in")
                  .description("Number of message incoming to the MessageTransformer step")
                  .register(Metrics.globalRegistry);

  private final PipelineConfig pipelineConfig;
  private final List<MatchTransformPair> matchTransformPairs;
  private ProcessorContext<String, ValueErrorMessage<TransformedMessage>> context;

  public MessageProcessor(PipelineConfig pipelineConfig) {
    this.pipelineConfig = pipelineConfig;
    this.matchTransformPairs =
            this.pipelineConfig.getSteps().values().stream()
                    .map(
                            x ->
                                    new MessageProcessor.MatchTransformPair(
                                            x.getMatch(), new Transformer(x.getTransform())))
                    .collect(Collectors.toList());
  }

  @Override
  public void init(ProcessorContext<String, ValueErrorMessage<TransformedMessage>> context) {
    this.context = context;
  }

  private Iterator<KeyValue<String, ValueErrorMessage<TransformedMessage>>> handleRecord(
      String key, DocumentContext context) {
    var transformed =
        matchTransformPairs.stream()
            .parallel()
            .filter(x -> x.getMatch().matchContext(context))
            .map(
                x ->
                    new TransformedMessage(
                        x.getTransformer().transformList(context), x.getMatch().getTag()))
            .map(x -> KeyValue.pair(key, new ValueErrorMessage<>(x)))
            .iterator();
    return transformed;
  }

  @Override
  public void process(Record<String, DocumentContext> record) {
    counterIn.increment();

    Sample timer = Timer.start(Metrics.globalRegistry);
    String currPipeline = "";

    String key = record.key();
    DocumentContext value = record.value();
    long ts = record.timestamp();

    try {
      Iterator<KeyValue<String, ValueErrorMessage<TransformedMessage>>> pairs =
          handleRecord(key, value);

      var counter = 0;
      while (pairs.hasNext()) {
        KeyValue<String, ValueErrorMessage<TransformedMessage>> kv = pairs.next();
        currPipeline = kv.value.getValue().getTag() == null ? "" : kv.value.getValue().getTag();
        context.forward(new Record<>(kv.key, kv.value, record.timestamp()));
        counter++;
      }
      // If no pipeline matches the message, then pass it down as it is
      if (counter == 0) {
        context.forward(
            new Record<>(
                key,
                new ValueErrorMessage<>(new TransformedMessage(List.of(value), null)),
                record.timestamp()));
      }

      // TODO: from this processor, we use pipeline name as metric Tag.
      //  However, "partition" can be also used (from context.recordMetadata().get().partition()).
      timer.stop(Metrics.globalRegistry.timer(timerMetric, metricTag, currPipeline));
    } catch (Exception e) {
      var error =
          new ErrorMessage(context, getClass().getName(), key, value.json(), ts, e.getMessage());
      context.forward(new Record<>(key, new ValueErrorMessage<>(null, error), record.timestamp()));

      timer.stop(Metrics.globalRegistry.timer(timerErrorMetric, metricTag, currPipeline));
    }
  }

  @Getter
  @AllArgsConstructor
  private static class MatchTransformPair {
    private Match match;
    private Transformer transformer;
  }
}
