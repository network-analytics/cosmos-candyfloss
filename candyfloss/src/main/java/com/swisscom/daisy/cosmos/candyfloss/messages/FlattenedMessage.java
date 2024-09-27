package com.swisscom.daisy.cosmos.candyfloss.messages;

import com.jayway.jsonpath.DocumentContext;
import lombok.AllArgsConstructor;
import lombok.Data;

/***
 * Holder to carry flattened message.
 * Flattened Message is after applying flatMap on a transformed Message
 */
@Data
@AllArgsConstructor
public class FlattenedMessage {
  private final DocumentContext value;
  private final String tag;
}
