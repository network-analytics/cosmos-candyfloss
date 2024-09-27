package com.swisscom.daisy.cosmos.candyfloss.config;

import com.jayway.jsonpath.DocumentContext;
import com.swisscom.daisy.cosmos.candyfloss.messages.FlattenedMessage;

public interface CounterKeyExtractor {
  String getKey(FlattenedMessage message);

  String getKey(DocumentContext context);
}
