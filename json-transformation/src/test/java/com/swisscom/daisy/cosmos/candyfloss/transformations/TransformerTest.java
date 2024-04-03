package com.swisscom.daisy.cosmos.candyfloss.transformations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swisscom.daisy.cosmos.candyfloss.testutils.JsonUtil;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;

class TransformerTest {
  ObjectMapper objectMapper = new ObjectMapper();

  public void testTransformation(String directory) throws IOException, JSONException {
    var config =
        JsonUtil.readJsonArray(getClass().getClassLoader().getResource(directory + "/config.json"));
    var transformerConfig = (List<Map<String, Object>>) config.get(0).get("transform");
    var input =
        JsonUtil.readJson(getClass().getClassLoader().getResource(directory + "/input.json"));
    var expected =
        JsonUtil.readJsonArray(
            getClass().getClassLoader().getResource(directory + "/expected.json"));
    var transformer = new Transformer(transformerConfig);
    var transformed = transformer.transformList(input);
    System.out.println(
        objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(transformed));
    JSONAssert.assertEquals(
        objectMapper.writeValueAsString(expected),
        objectMapper.writeValueAsString(transformed),
        true);
  }

  @Test
  public void testTransformOpenconfigInterfaces() throws IOException, JSONException {
    testTransformation("openconfig-interfaces");
  }

  @Test
  public void testTransformCiscoIoNpOperCounters() throws IOException, JSONException {
    testTransformation("cisco-io-np-oper-counters");
  }

  @Test
  public void testTransformOpenconfigInterfaces2() throws IOException {
    // var spec = readJsonArray("test.json");
    // var transform = (List<Map<String, Object>>) spec.get(0).get("transform");
    //// System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(spec));
    // Transformer transformer = new Transformer(transform);
    // var input = readJson("openconfig-interface.json");
    //// var input = readJson("cisco-np-oper-counters.json");
    // var output = transformer.transformList(input).stream().filter(x -> x != null).toList();
    //// var output = transformer.transform(input);
    // System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(output));
  }
}
