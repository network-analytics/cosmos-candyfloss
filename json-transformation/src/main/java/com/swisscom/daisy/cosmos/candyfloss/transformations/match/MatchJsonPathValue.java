package com.swisscom.daisy.cosmos.candyfloss.transformations.match;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MatchJsonPathValue implements Match {
  private final JsonPath path;
  private final Object value;
  private final String tag;

  public boolean match(Object jsonObject) {
    try {
      return path.read(jsonObject).equals(value);
    } catch (PathNotFoundException e) {
      return false;
    }
  }

  public boolean matchContext(DocumentContext context) {
    var matched = context.read(path);
    return matched != null && matched.equals(value);
  }
}
