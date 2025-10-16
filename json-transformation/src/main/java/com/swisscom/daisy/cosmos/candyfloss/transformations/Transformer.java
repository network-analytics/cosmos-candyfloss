package com.swisscom.daisy.cosmos.candyfloss.transformations;

import com.bazaarvoice.jolt.Chainr;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Transformer {
  private static final Configuration configuration =
      Configuration.builder().options(Option.SUPPRESS_EXCEPTIONS).build();

  private final Chainr chainr;

  public Transformer(List<Map<String, Object>> transformationSpec) {
    this.chainr = Chainr.fromSpec(transformationSpec);
  }

  public Map<String, Object> transform(Map<String, Object> input) {
    return (Map<String, Object>) chainr.transform(input);
  }

  public DocumentContext transform(DocumentContext input) {
    var transformed = (Map<String, Object>) chainr.transform(input.json());
    return transformed == null ? null : JsonPath.using(configuration).parse(transformed);
  }

  public List<Map<String, Object>> transformList(Map<String, Object> input) {
    return (List<Map<String, Object>>) chainr.transform(input);
  }

  public List<DocumentContext> transformList(DocumentContext input) {
    var transformed = (List<Map<String, Object>>) chainr.transform(input.json());
    return transformed == null
        ? Collections.emptyList()
        : transformed.stream()
            .filter(Objects::nonNull)
            .map(x -> JsonPath.using(configuration).parse(x))
            .toList();
  }
}
