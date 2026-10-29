package com.srm.credit.exception;

public class FxRateConflictException extends RuntimeException {
  public FxRateConflictException() {
    super("Já existe cotação para essa vigência.");
  }
}
