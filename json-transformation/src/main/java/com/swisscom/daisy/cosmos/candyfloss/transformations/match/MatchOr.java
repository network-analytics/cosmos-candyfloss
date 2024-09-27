package com.swisscom.daisy.cosmos.candyfloss.transformations.match;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

/***
 * Implements logical "or" operation over multiple matches
 */
@AllArgsConstructor
@Getter
public class MatchOr implements Match {
  private final List<Match> innerMatches;
  private static final Configuration configuration =
      Configuration.builder().options(Option.SUPPRESS_EXCEPTIONS).build();

  private final String tag;

  @Override
  public boolean match(Object jsonObject) {
    DocumentContext context = JsonPath.using(configuration).parse(jsonObject);

    for (var match : innerMatches) {
      if (match.matchContext(context)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean matchContext(DocumentContext context) {
    for (var match : innerMatches) {
      if (match.matchContext(context)) {
        return true;
      }
    }
    return false;
  }
}
