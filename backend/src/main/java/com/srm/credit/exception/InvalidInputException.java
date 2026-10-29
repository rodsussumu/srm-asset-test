package com.srm.credit.exception;

public class InvalidInputException extends RuntimeException {
  private final String field;

  public InvalidInputException(String field, String message) {
    super(message);
    this.field = field;
  }

  public String field() {
    return field;
  }
}
