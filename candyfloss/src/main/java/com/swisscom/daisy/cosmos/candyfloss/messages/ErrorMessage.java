package com.swisscom.daisy.cosmos.candyfloss.messages;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.internals.ProcessorRecordContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/***
 * Error message is what we send to the dead-letter-queue for any failure in the pipeline.
 */
@Data
@AllArgsConstructor
public class ErrorMessage {
  private static final Logger logger = LoggerFactory.getLogger(ErrorMessage.class);

  private final long timestamp;
  private final String applicationId;
  private final String taskId;
  private final int partition;
  private final long offset;

  private final String processingStep;
  private final String key;

  private final String errorMessage;
  private final String failedValue;

  private static final ObjectMapper objectMapper = new ObjectMapper();

  public <KForward, VForward> ErrorMessage(
      final ProcessorContext<KForward, VForward> context,
      final String processingStep,
      final String key,
      final Object value,
      final long ts,
      final String errorMessage) {

    var originalMessage = "";
    try {
      originalMessage = objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException ex) {
      originalMessage = "{\"error\":\"" + ex.getMessage() + "\"}";
    }
    logger.error(
        "Encounter error (processingStep={}, partition={}, offset={}): {} on message: {}",
        processingStep,
        context
            .recordMetadata()
            .orElse(new ProcessorRecordContext(-1L, -1L, -1, null, new RecordHeaders()))
            .partition(),
        context
            .recordMetadata()
            .orElse(new ProcessorRecordContext(-1L, -1L, -1, null, new RecordHeaders()))
            .offset(),
        errorMessage,
        originalMessage);
    this.timestamp = ts;
    this.applicationId = context.applicationId();
    this.taskId = context.taskId().toString();
    this.partition = context.taskId().partition();
    this.offset =
        context
            .recordMetadata()
            .orElse(new ProcessorRecordContext(-1L, -1L, -1, null, new RecordHeaders()))
            .offset();
    this.processingStep = processingStep;
    this.key = key;
    this.errorMessage = errorMessage;
    this.failedValue = originalMessage;
  }
}
