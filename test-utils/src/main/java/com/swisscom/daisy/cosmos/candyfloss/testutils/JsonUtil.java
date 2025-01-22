package com.swisscom.daisy.cosmos.candyfloss.testutils;

import static com.swisscom.daisy.cosmos.candyfloss.transformations.jolt.CustomFunctions.factory;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/***
 * Json helper functions to read test fixtures
 */
public class JsonUtil {
  public static String readFromInputStream(InputStream inputStream) throws IOException {
    StringBuilder resultStringBuilder = new StringBuilder();
    try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream))) {
      String line;
      while ((line = br.readLine()) != null) {
        resultStringBuilder.append(line).append("\n");
      }
    }
    return resultStringBuilder.toString();
  }

  public static Map<String, Object> readJson(InputStream inputStream) throws IOException {
    if (inputStream == null) {
      throw new NullPointerException("inputStream is null");
    }
    var jsonString = JsonUtil.readFromInputStream(inputStream);
    ObjectMapper objectMapper = new ObjectMapper(factory);
    return objectMapper.readValue(jsonString, Map.class);
  }

  public static Map<String, Object> readJson(URL url) throws IOException {
    if (url == null) {
      throw new NullPointerException("URL is null");
    }
    return readJson(url.openStream());
  }

  public static List<Map<String, Object>> readJsonArray(InputStream inputStream)
      throws IOException {
    ObjectMapper objectMapper = new ObjectMapper(factory);
    List<Map<String, Object>> result = new ArrayList<>();
    var jsonString = JsonUtil.readFromInputStream(inputStream);
    var iterator = objectMapper.readerFor(Map.class).readValues(jsonString);
    while (iterator.hasNext()) result.add((Map) iterator.next());
    return result;
  }

  public static List<Map<String, Object>> readJsonArray(URL url) throws IOException {
    if (url == null) {
      throw new NullPointerException("URL is null");
    }
    return readJsonArray(url.openStream());
  }
}
