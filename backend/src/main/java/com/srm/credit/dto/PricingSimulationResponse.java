package com.srm.credit.dto;

import com.srm.credit.model.PaymentCurrency;
import com.srm.credit.model.ReceivableType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;

@Schema(
    description =
        "Preço indicativo; valores e taxas são strings decimais. Nenhuma gravação financeira é feita.")
public record PricingSimulationResponse(
    boolean indicative,
    String faceValue,
    String faceCurrency,
    LocalDate dueDate,
    ReceivableType type,
    String presentValueBrl,
    String discountBrl,
    String paymentAmount,
    PaymentCurrency paymentCurrency,
    int months,
    String baseRate,
    String spread,
    Instant operationAt,
    LocalDate operationDate,
    String businessTimezone,
    String termConvention,
    String roundingMode,
    String policyVersion,
    @Schema(description = "Snapshot da cotação selecionada em USD; null em BRL.")
        FxRateResponse fxRate) {}
