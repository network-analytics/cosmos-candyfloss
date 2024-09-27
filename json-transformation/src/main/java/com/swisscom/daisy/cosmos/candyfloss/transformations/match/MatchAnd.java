package com.swisscom.daisy.cosmos.candyfloss.transformations.match;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
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
  private static final Configuration configuration =
      Configuration.builder().options(Option.SUPPRESS_EXCEPTIONS).build();

  private final String tag;

  @Override
  public boolean match(Object jsonObject) {

    DocumentContext context = JsonPath.using(configuration).parse(jsonObject);
    return this.innerMatches.stream().allMatch(m -> m.matchContext(context));
  }

  @Override
  public boolean matchContext(DocumentContext context) {
    return this.innerMatches.stream().allMatch(m -> m.matchContext(context));
  }
}
