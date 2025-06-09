package com.swisscom.daisy.cosmos.candyfloss.monitors;

import io.micrometer.core.instrument.Metrics;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.processor.StateRestoreListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RestoreLogger implements StateRestoreListener {
  private static final Logger logger = LoggerFactory.getLogger(RestoreLogger.class);
  private static final String cfRestoreCounter = "cf_restore_records";
  private static final String metricTagThread = "thread";
  private static final String metricTagPartition = "partition";

  @Override
  public void onRestoreStart(
      TopicPartition changelogPartition, String storeName, long beginOffset, long endOffset) {
    logger.info(
        "START - State restore from changelog partition: {}, from offset: {} to offset: {}",
        changelogPartition.partition(),
        beginOffset,
        endOffset);
  }

  @Override
  public void onBatchRestored(
      TopicPartition changelogPartition, String storeName, long offset, long numRestored) {
    String partition = String.valueOf(changelogPartition.partition());
    String threadName = Thread.currentThread().getName();

    Metrics.globalRegistry
        .counter(cfRestoreCounter, metricTagThread, threadName, metricTagPartition, partition)
        .increment(numRestored);
  }

  @Override
  public void onRestoreEnd(
      TopicPartition changelogPartition, String storeName, long totalRestored) {
    logger.info(
        "END - State restore from changelog partition: {}, total restored: {}",
        changelogPartition.partition(),
        totalRestored);
  }

  public static void setListener(KafkaStreams kafkaStreams, StateRestoreListener listener) {
    logger.info("Enable Candyfloss restore monitoring (logging and metrics)");
    kafkaStreams.setGlobalStateRestoreListener(listener);
  }
}
