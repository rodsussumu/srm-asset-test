package com.srm.credit.exception;

public class FxProviderUnavailableException extends RuntimeException {
  public FxProviderUnavailableException() {
    super("Não foi possível sincronizar a cotação.");
  }
}
