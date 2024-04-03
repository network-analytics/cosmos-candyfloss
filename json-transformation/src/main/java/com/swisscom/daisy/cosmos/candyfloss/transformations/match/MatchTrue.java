package com.swisscom.daisy.cosmos.candyfloss.transformations.match;

import lombok.AllArgsConstructor;
import lombok.Getter;

/***
 * A match that always returns true
 */
@Getter
@AllArgsConstructor
public class MatchTrue implements Match {
  private final String tag;

  @Override
  public boolean match(Object jsonObject) {
    return true;
  }
}
