package com.swisscom.daisy.cosmos.candyfloss.transformations.match;

import lombok.AllArgsConstructor;
import lombok.Getter;

/***
 * A match that always returns False
 */
@Getter
@AllArgsConstructor
public class MatchFalse implements Match {
  private final String tag;

  @Override
  public boolean match(Object jsonObject) {
    return false;
  }
}
