package com.swisscom.daisy.cosmos.candyfloss.monitors;

import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KafkaStreams.StateListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StateLogger implements StateListener {
  private final KafkaStreams streams;

  public StateLogger(KafkaStreams ks) {
    streams = ks;
  }

  private static final Logger logger = LoggerFactory.getLogger(StateLogger.class);

  private void displayCurrentPartitionInfo() {
    streams
        .metadataForLocalThreads()
        .forEach(
            metadata -> {
              if (!metadata.activeTasks().isEmpty()) {
                metadata
                    .activeTasks()
                    .forEach(
                        partition ->
                            logger.info(
                                "[{}] assigned partition: {}",
                                Thread.currentThread().getName(),
                                partition.topicPartitions()));
              }
            });
  }

  @Override
  public void onChange(KafkaStreams.State newState, KafkaStreams.State oldState) {
    logger.info(
        "[Candyfloss] State transition from oldState: {} to newState: {}", oldState, newState);
    switch (newState) {
      case RUNNING:
        displayCurrentPartitionInfo();
        break;
      case CREATED:
      case REBALANCING:
      case PENDING_SHUTDOWN:
      case NOT_RUNNING:
      case ERROR:
      default:
        break;
    }
  }

  public static void setListener(KafkaStreams kafkaStreams, StateListener listener) {
    logger.info("Enable Candyfloss state listener");
    kafkaStreams.setStateListener(listener);
  }
}
