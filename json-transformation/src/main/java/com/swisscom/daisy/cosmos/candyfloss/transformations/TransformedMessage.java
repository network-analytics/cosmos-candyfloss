package com.swisscom.daisy.cosmos.candyfloss.transformations;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;

/***
 * Return both the configs used to transform the message and the transformed message.
 *
 * The idea, is to hold additional configurations needed for further processing, e.g., deciding on
 * the output topic, or counter normalization.
 */
@Getter
@AllArgsConstructor
public class TransformedMessage {
  private final List<Map<String, Object>> value;
  private final String tag;

  public TransformedMessage(List<Map<String, Object>> value) {
    this.value = value;
    tag = null;
  }
}
