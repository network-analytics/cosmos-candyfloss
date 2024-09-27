package com.swisscom.daisy.cosmos.candyfloss.config;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.JsonPathException;
import com.swisscom.daisy.cosmos.candyfloss.messages.FlattenedMessage;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class JsonPathCounterKeyExtractor implements CounterKeyExtractor {
  private JsonPath jsonPath;

  public JsonPathCounterKeyExtractor(String jsonPathString) {
    jsonPath = JsonPath.compile(jsonPathString);
  }

  @Override
  public String getKey(FlattenedMessage message) throws JsonPathException {
    var ret = jsonPath.read(message.getValue());
    return ret instanceof String ? (String) ret : ret.toString();
  }

  @Override
  public String getKey(DocumentContext context) {
    var ret = context.read(jsonPath);
    return ret instanceof String ? (String) ret : ret.toString();
  }
}
