package com.srm.credit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record CreateFxRateRequest(
    @NotBlank
        @Schema(
            type = "string",
            example = "5.4321",
            description = "BRL por USD; positivo, até 8 casas e 8 dígitos inteiros.")
        String rate,
    @NotBlank @Size(max = 100) String origin,
    @NotNull
        @Schema(
            description =
                "Vigência; precisão máxima de microssegundos. Futuras são armazenadas, mas não selecionadas antes da vigência.")
        Instant effectiveAt) {}
