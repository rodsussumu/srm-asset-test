package com.srm.credit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateAssignorRequest(
    @NotBlank(message = "Nome obrigatório.")
        @Size(max = 200, message = "Nome deve ter até 200 caracteres.")
        @Schema(example = "Empresa Cedente Ltda.")
        String name) {}
