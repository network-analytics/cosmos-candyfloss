package com.swisscom.daisy.cosmos.candyfloss.config;

import com.swisscom.daisy.cosmos.candyfloss.messages.FlattenedMessage;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ConstantCounterKeyExtractor implements CounterKeyExtractor {
  private final String constantKey;

  @Override
  public String getKey(FlattenedMessage message) {
    return constantKey;
  }
}
