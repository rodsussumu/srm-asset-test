package com.srm.credit.dto;

import com.srm.credit.model.PaymentCurrency;
import com.srm.credit.model.ReceivableType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.util.UUID;

public record CreateReceivableRequest(
    @NotNull(message = "Cedente obrigatório.") UUID assignorId,
    @NotBlank(message = "Valor de face obrigatório.")
        @Pattern(
            regexp = "(?:0|[1-9][0-9]{0,15})(?:\\.[0-9]{1,2})?",
            message = "Informe uma string decimal com até duas casas e 16 dígitos inteiros.")
        @Schema(type = "string", example = "100000.00")
        String faceValue,
    @NotNull(message = "Vencimento obrigatório.")
        @Schema(description = "De 1 a 1080 dias após a operação em America/Sao_Paulo.")
        LocalDate dueDate,
    @NotNull(message = "Tipo obrigatório.") ReceivableType type,
    @NotNull(message = "Moeda de pagamento obrigatória.") PaymentCurrency paymentCurrency) {}
