package com.swisscom.daisy.cosmos.candyfloss.processors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swisscom.daisy.cosmos.candyfloss.config.PipelineConfig;
import com.swisscom.daisy.cosmos.candyfloss.messages.FlattenedMessage;
import com.swisscom.daisy.cosmos.candyfloss.messages.OutputMessage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;

public class ToJsonProcessor implements Processor<String, FlattenedMessage, String, OutputMessage> {
  private final Counter counterError =
      Counter.builder("json_streams_json_serialize_error")
          .description("Number of error messages that are discarded to dlq topic")
          .register(Metrics.globalRegistry);
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final String dqlTopicName;
  private final String discardTopicName;
  private final PipelineConfig pipelineConfig;

  private ProcessorContext<String, OutputMessage> context;

  public ToJsonProcessor(
      String dlqTopicName, String discardTopicName, PipelineConfig pipelineConfig) {
    this.dqlTopicName = dlqTopicName;
    this.discardTopicName = discardTopicName;
    this.pipelineConfig = pipelineConfig;
  }

  @Override
  public void init(ProcessorContext<String, OutputMessage> context) {
    this.context = context;
  }

  public KeyValue<String, OutputMessage> handleRecord(String key, FlattenedMessage value) {
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
  public void process(Record<String, FlattenedMessage> record) {
    var key = record.key();
    var value = record.value();

    var kv = handleRecord(key, value);
    context.forward(new Record<>(kv.key, kv.value, record.timestamp()));
  }
}
