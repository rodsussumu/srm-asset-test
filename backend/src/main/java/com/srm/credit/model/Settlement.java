package com.srm.credit.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record Settlement(
    UUID id,
    UUID receivableId,
    UUID assignorId,
    String assignorName,
    String actorId,
    BigDecimal faceValue,
    LocalDate dueDate,
    ReceivableType type,
    int months,
    LocalDate operationDate,
    Instant operationAt,
    BigDecimal baseRate,
    BigDecimal spread,
    BigDecimal presentValueBrl,
    BigDecimal discountBrl,
    PaymentCurrency paymentCurrency,
    BigDecimal paymentAmount,
    UUID fxRateId,
    BigDecimal fxRate,
    String fxOrigin,
    Instant fxEffectiveAt,
    Instant fxRecordedAt,
    String policyVersion,
    UUID correlationId,
    Instant settledAt,
    long receivableVersion) {}
