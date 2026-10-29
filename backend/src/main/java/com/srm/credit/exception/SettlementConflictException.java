package com.srm.credit.exception;

public class SettlementConflictException extends RuntimeException {
  public enum Code {
    PRICE_CHANGED,
    IDEMPOTENCY_KEY_REUSED,
    REQUEST_IN_PROGRESS,
    RECEIVABLE_ALREADY_SETTLED
  }

  private final Code code;

  public SettlementConflictException(Code code) {
    super(code.name());
    this.code = code;
  }

  public Code code() {
    return code;
  }
}
