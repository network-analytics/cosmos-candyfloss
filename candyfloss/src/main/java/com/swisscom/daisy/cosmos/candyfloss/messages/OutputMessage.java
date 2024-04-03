package com.swisscom.daisy.cosmos.candyfloss.messages;

import lombok.AllArgsConstructor;
import lombok.Data;

/***
 * Last step for outputting a message
 * The name of topic is included for dynamic selection of output topic
 */
@Data
@AllArgsConstructor
public class OutputMessage {
  private final String outputTopic;
  private final String value;
}
