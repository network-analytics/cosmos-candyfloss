package com.swisscom.daisy.cosmos.candyfloss.transformations.match;

import com.jayway.jsonpath.DocumentContext;
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

  @Override
  public boolean matchContext(DocumentContext context) {
    return false;
  }
}
