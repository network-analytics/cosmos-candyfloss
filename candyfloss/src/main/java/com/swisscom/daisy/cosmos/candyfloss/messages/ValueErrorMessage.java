package com.swisscom.daisy.cosmos.candyfloss.messages;

import lombok.AllArgsConstructor;
import lombok.Data;

/***
 * Generic holder that holds any value or error message
 * @param <T>
 */
@Data
@AllArgsConstructor
public class ValueErrorMessage<T> {
  private T value;
  private ErrorMessage errorMessage;

  public ValueErrorMessage(T value) {
    this.value = value;
    this.errorMessage = null;
  }

  public boolean isError() {
    return errorMessage != null;
  }
}
