package com.swisscom.daisy.cosmos.candyfloss.transformations.match;

import com.jayway.jsonpath.DocumentContext;

/***
 * Generic Match logical expression
 *
 * Allows to abstract a logical expression to something that return true and a user input tag
 */
public interface Match {
  /***
   * Returns true of the logical expression evaluates to true over the given jsonObject
   */
  boolean match(Object jsonObject);

  /***
   * Same as {@link match} except it works on pre-processed json document for faster evaluation
   */
  boolean matchContext(DocumentContext jsonString);

  /***
   * User given tag for the match, useful tag multiples expressions then figure out which one was used
   */
  String getTag();
}
