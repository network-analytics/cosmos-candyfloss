package com.swisscom.daisy.cosmos.candyfloss.transformations.match;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

/***
 * Implements logical "and" operation over multiple matches
 */
@AllArgsConstructor
@Getter
public class MatchAnd implements Match {
  private final List<Match> innerMatches;

  private final String tag;

  @Override
  public boolean match(Object jsonObject) {
    return this.innerMatches.stream().map(m -> m.match(jsonObject)).allMatch(r -> r);
  }
}
