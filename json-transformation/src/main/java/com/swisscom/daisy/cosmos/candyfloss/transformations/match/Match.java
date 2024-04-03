package com.swisscom.daisy.cosmos.candyfloss.transformations.match;

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
   * User given tag for the match, useful tag multiples expressions then figure out which one was used
   */
  String getTag();
}
