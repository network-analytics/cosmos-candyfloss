package com.swisscom.daisy.cosmos.candyfloss.transformations.match;

import com.jayway.jsonpath.DocumentContext;
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

  @Override
  public boolean matchContext(DocumentContext context) {
    return true;
  }
}
