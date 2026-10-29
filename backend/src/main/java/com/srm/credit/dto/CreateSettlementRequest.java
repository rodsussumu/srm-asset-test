package com.srm.credit.dto;

import com.srm.credit.model.PaymentCurrency;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateSettlementRequest(
    @NotNull UUID receivableId,
    @NotNull PaymentCurrency currency,
    @NotBlank
        @Schema(
            type = "string",
            example = "17094.67",
            description =
                "Pagamento esperado; string não negativa com até duas casas, confirmada pelo operador.")
        String expectedPaymentAmount,
    @Schema(description = "Obrigatório em USD, ausente em BRL.") UUID expectedFxRateId) {}
