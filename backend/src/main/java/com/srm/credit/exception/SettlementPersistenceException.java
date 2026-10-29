package com.srm.credit.exception;

public class SettlementPersistenceException extends RuntimeException {
  public SettlementPersistenceException(Throwable cause) {
    super("Falha na persistência financeira.", cause);
  }
}
