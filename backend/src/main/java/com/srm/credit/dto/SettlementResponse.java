package com.srm.credit.dto;

import com.srm.credit.model.PaymentCurrency;
import com.srm.credit.model.ReceivableType;
import com.srm.credit.model.Settlement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record SettlementResponse(
    UUID id,
    UUID receivableId,
    UUID assignorId,
    String assignorName,
    String actorId,
    String faceValue,
    String faceCurrency,
    LocalDate dueDate,
    ReceivableType type,
    int months,
    LocalDate operationDate,
    Instant operationAt,
    String baseRate,
    String spread,
    String presentValueBrl,
    String discountBrl,
    PaymentCurrency paymentCurrency,
    String paymentAmount,
    FxRateResponse fxRate,
    String policyVersion,
    UUID correlationId,
    Instant settledAt,
    long receivableVersion) {
  public static SettlementResponse from(Settlement value) {
    FxRateResponse fx =
        value.fxRateId() == null
            ? null
            : new FxRateResponse(
                value.fxRateId(),
                "USD/BRL",
                "BRL per USD",
                value.fxRate().setScale(8).toPlainString(),
                value.fxOrigin(),
                value.fxEffectiveAt(),
                value.fxRecordedAt());
    return new SettlementResponse(
        value.id(),
        value.receivableId(),
        value.assignorId(),
        value.assignorName(),
        value.actorId(),
        value.faceValue().setScale(2).toPlainString(),
        "BRL",
        value.dueDate(),
        value.type(),
        value.months(),
        value.operationDate(),
        value.operationAt(),
        value.baseRate().setScale(8).toPlainString(),
        value.spread().setScale(8).toPlainString(),
        value.presentValueBrl().setScale(2).toPlainString(),
        value.discountBrl().setScale(2).toPlainString(),
        value.paymentCurrency(),
        value.paymentAmount().setScale(2).toPlainString(),
        fx,
        value.policyVersion(),
        value.correlationId(),
        value.settledAt(),
        value.receivableVersion());
  }
}
