package com.swisscom.daisy.cosmos.candyfloss.transformations.jolt;

import com.bazaarvoice.jolt.common.Optional;
import com.bazaarvoice.jolt.modifier.function.Function;
import com.bazaarvoice.jolt.modifier.function.Objects;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/***
 * Custom functions to be used in Jolt transformations.
 */
public class CustomFunctions {
  public static final JsonFactory factory =
      JsonFactory.builder().enable(JsonReadFeature.ALLOW_JAVA_COMMENTS).build();

  public static final class jsonStringToJson extends Function.SingleFunction<Object> {
    private static final Logger logger = LoggerFactory.getLogger(CustomFunctions.class);

    private static final ObjectMapper objectMapper = new ObjectMapper(factory);

    @Override
    protected Optional<Object> applySingle(Object arg) {
      if (arg == null) {
        return Optional.empty();
      }
      String jsonString = ((String) arg).trim();
      try {
        Object jsonObj = objectMapper.readerFor(Object.class).readValue(jsonString);
        return Optional.of(jsonObj);
      } catch (IOException e) {
        logger.error(
            "Couldn't extract JSON object form a JSON String: '{}' due to error: {}",
            arg,
            e.getMessage());
        throw new RuntimeException(e);
      }
    }
  }

  public static final class multiply extends Function.ListFunction {
    @Override
    protected Optional<Object> applyList(List<Object> argList) {
      if (argList == null || argList.size() != 2) {
        return Optional.empty();
      }
      Optional<? extends Number> numerator = Objects.toNumber(argList.get(0));
      Optional<? extends Number> denominator = Objects.toNumber(argList.get(1));
      if (numerator.isPresent() && denominator.isPresent()) {
        long drLongValue = denominator.get().longValue();
        long nrLongValue = numerator.get().longValue();
        long result = java.lang.Math.multiplyExact(nrLongValue, drLongValue);
        return Optional.of(result);
      }
      return Optional.empty();
    }
  }
}
