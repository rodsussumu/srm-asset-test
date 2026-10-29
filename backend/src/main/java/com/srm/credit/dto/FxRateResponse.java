package com.srm.credit.dto;

import com.srm.credit.model.FxRate;
import java.time.Instant;
import java.util.UUID;

public record FxRateResponse(
    UUID id,
    String pair,
    String direction,
    String rate,
    String origin,
    Instant effectiveAt,
    Instant recordedAt) {
  public static FxRateResponse from(FxRate rate) {
    return new FxRateResponse(
        rate.getId(),
        rate.getPair(),
        "BRL per USD",
        rate.getRate().toPlainString(),
        rate.getOrigin(),
        rate.getEffectiveAt(),
        rate.getRecordedAt());
  }
}
