package com.swisscom.daisy.cosmos.candyfloss.transformations.match;

import com.jayway.jsonpath.JsonPath;
import com.swisscom.daisy.cosmos.candyfloss.transformations.match.exceptions.InvalidMatchConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unchecked")
public class MatchBuilder {
  public static Match fromJson(Map<String, Object> match, String tag)
      throws InvalidMatchConfiguration {
    // No match defined, assume always true
    if (match.isEmpty()) {
      return new MatchTrue(tag);
    }
    if (match.containsKey("jsonpath")) {
      var path = JsonPath.compile((String) match.get("jsonpath"));
      if (match.containsKey("value")) {
        var value = match.get("value");
        return new MatchJsonPathValue(path, value, tag);
      } else {
        return new MatchJsonPathExists(path, tag);
      }
    } else if (match.containsKey("and")) {
      List<Map<String, Object>> inner = (List<Map<String, Object>>) match.get("and");
      List<Match> innerMatches = new ArrayList<>();
      for (var x : inner) {
        innerMatches.add(MatchBuilder.fromJson(x, tag));
      }
      return new MatchAnd(innerMatches, tag);
    } else if (match.containsKey("or")) {
      List<Map<String, Object>> inner = (List<Map<String, Object>>) match.get("or");
      List<Match> innerMatches = new ArrayList<>();
      for (var x : inner) {
        innerMatches.add(MatchBuilder.fromJson(x, tag));
      }
      return new MatchOr(innerMatches, tag);
    } else if (match.containsKey("constant")) {
      var value = (Boolean) match.get("constant");
      if (value) {
        return new MatchTrue(tag);
      } else {
        return new MatchFalse(tag);
      }
    } else if (match.containsKey("not")) {
      Map<String, Object> inner = (Map<String, Object>) match.get("not");
      var notMatch = MatchBuilder.fromJson(inner, tag);
      return new MatchNot(notMatch, tag);
    }

    throw new InvalidMatchConfiguration(
        String.format(
            "Didn't find any valid match option in %s", String.join(",", match.keySet())));
  }
}
