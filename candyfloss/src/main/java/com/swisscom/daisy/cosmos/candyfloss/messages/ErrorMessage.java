package com.swisscom.daisy.cosmos.candyfloss.messages;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.kafka.streams.processor.ProcessorContext;
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

  public ErrorMessage(
      ProcessorContext context,
      String processingStep,
      String key,
      String errorMessage,
      String failedValue) {
    this(
        context.timestamp(),
        context.applicationId(),
        context.taskId().toString(),
        context.partition(),
        context.offset(),
        processingStep,
        key,
        errorMessage,
        failedValue);
  }

  public static ErrorMessage getError(
      final ProcessorContext context,
      final String processingStep,
      final String key,
      final Object value,
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
        context.partition(),
        context.offset(),
        errorMessage,
        originalMessage);
    return new ErrorMessage(context, processingStep, key, errorMessage, originalMessage);
  }
}
