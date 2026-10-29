package com.srm.credit.dto;

import com.srm.credit.model.PaymentCurrency;
import com.srm.credit.model.ReceivableType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record PricingSimulationRequest(
    @NotBlank
        @Schema(
            type = "string",
            example = "100000.00",
            description = "Face em BRL, positiva; até 16 dígitos inteiros e 2 casas.")
        String faceValue,
    @NotNull
        @Schema(
            description = "Vencimento entre 1 e 1080 dias após a operação em America/Sao_Paulo.")
        LocalDate dueDate,
    @NotNull ReceivableType type,
    @NotNull PaymentCurrency paymentCurrency) {}
