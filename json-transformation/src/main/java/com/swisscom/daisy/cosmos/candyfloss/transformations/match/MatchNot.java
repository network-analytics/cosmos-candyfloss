package com.swisscom.daisy.cosmos.candyfloss.transformations.match;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import lombok.AllArgsConstructor;
import lombok.Getter;

/***
 * A match that always returns true
 */
@Getter
@AllArgsConstructor
public class MatchNot implements Match {
    private final Match innerMatch;
    private static final Configuration configuration =
            Configuration.builder().options(Option.SUPPRESS_EXCEPTIONS).build();

    private final String tag;

    @Override
    public boolean match(Object jsonObject) {
        DocumentContext context = JsonPath.using(configuration).parse(jsonObject);
        return !this.innerMatch.matchContext(context);
    }

    @Override
    public boolean matchContext(DocumentContext context) {
        return !this.innerMatch.matchContext(context);
    }
}
