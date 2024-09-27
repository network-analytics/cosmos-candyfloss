package com.swisscom.daisy.cosmos.candyfloss.transformations.match;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MatchJsonPathExists implements Match {
  private final JsonPath path;
  private final String tag;

  public boolean match(Object jsonObject) {
    try {
      var ret = path.read(jsonObject);
      if (ret == null) {
        return false;
      } else return !(ret instanceof List) || !((List<?>) ret).isEmpty();
    } catch (PathNotFoundException e) {
      return false;
    }
  }

  public boolean matchContext(DocumentContext context) {
    var ret = context.read(path);
    if (ret == null) {
      return false;
    } else return !(ret instanceof List) || !((List<?>) ret).isEmpty();
  }
}
