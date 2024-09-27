package com.swisscom.daisy.cosmos.candyfloss.transformers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swisscom.daisy.cosmos.candyfloss.config.PipelineConfig;
import com.swisscom.daisy.cosmos.candyfloss.messages.FlattenedMessage;
import com.swisscom.daisy.cosmos.candyfloss.messages.OutputMessage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.processor.ProcessorContext;

public class ToJsonTransformer
    implements Transformer<String, FlattenedMessage, KeyValue<String, OutputMessage>> {
  private final Counter counterError =
      Counter.builder("json_streams_json_serialize_error")
          .description("Number of error messages that are discarded to dlq topic")
          .register(Metrics.globalRegistry);
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final String dqlTopicName;
  private final String discardTopicName;
  private final PipelineConfig pipelineConfig;

  public ToJsonTransformer(
      String dlqTopicName, String discardTopicName, PipelineConfig pipelineConfig) {
    this.dqlTopicName = dlqTopicName;
    this.discardTopicName = discardTopicName;
    this.pipelineConfig = pipelineConfig;
  }

  @Override
  public void init(ProcessorContext context) {}

  @Override
  public KeyValue<String, OutputMessage> transform(String key, FlattenedMessage value) {
    try {
      // Message was successfully transformed, we need to serialize it
      String topicName;
      if (value.getTag() == null) {
        // Message didn't match anything, so we put it in a special topic
        topicName = this.discardTopicName;
      } else {
        topicName = pipelineConfig.getSteps().get(value.getTag()).getOutputTopic();
      }
      return KeyValue.pair(key, new OutputMessage(topicName, value.getValue().jsonString()));

    } catch (Exception e) {
      counterError.increment();
      return KeyValue.pair(
          key,
          new OutputMessage(this.dqlTopicName, String.format("\"error\": \"%s\"", e.getMessage())));
    }
  }

  @Override
  public void close() {}
}
