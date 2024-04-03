package com.swisscom.daisy.cosmos.candyfloss.transformations;

import com.bazaarvoice.jolt.Chainr;
import java.util.List;
import java.util.Map;

public class Transformer {
  private final Chainr chainr;

  public Transformer(List<Map<String, Object>> transformationSpec) {
    this.chainr = Chainr.fromSpec(transformationSpec);
  }

  public Map<String, Object> transform(Map<String, Object> input) {
    return (Map<String, Object>) chainr.transform(input);
  }

  public List<Map<String, Object>> transformList(Map<String, Object> input) {
    return (List<Map<String, Object>>) chainr.transform(input);
  }
}
