package com.swisscom.daisy.cosmos.candyfloss.messages;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;

/***
 * Holder to carry flattened message.
 * Flattened Message is after applying flatMap on a transformed Message
 */
@Data
@AllArgsConstructor
public class FlattenedMessage {
  private final Map<String, Object> value;
  private final String tag;
}
