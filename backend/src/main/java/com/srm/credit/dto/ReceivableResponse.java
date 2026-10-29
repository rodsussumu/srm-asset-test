package com.srm.credit.dto;

import com.srm.credit.model.PaymentCurrency;
import com.srm.credit.model.Receivable;
import com.srm.credit.model.ReceivableStatus;
import com.srm.credit.model.ReceivableType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ReceivableResponse(
    UUID id,
    UUID assignorId,
    @Schema(type = "string", example = "100000.00") String faceValue,
    String faceCurrency,
    LocalDate dueDate,
    ReceivableType type,
    PaymentCurrency paymentCurrency,
    ReceivableStatus status,
    Long version,
    Instant createdAt) {
  public static ReceivableResponse from(Receivable receivable) {
    return new ReceivableResponse(
        receivable.getId(),
        receivable.getAssignor().getId(),
        receivable.getFaceValue().toPlainString(),
        receivable.getFaceCurrency(),
        receivable.getDueDate(),
        receivable.getType(),
        receivable.getPaymentCurrency(),
        receivable.getStatus(),
        receivable.getVersion(),
        receivable.getCreatedAt());
  }
}
