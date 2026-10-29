package com.srm.credit.exception;

public class FxRateUnavailableException extends RuntimeException {
  public FxRateUnavailableException() {
    super("Cotação vigente indisponível ou expirada.");
  }
}
