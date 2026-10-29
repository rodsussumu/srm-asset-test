package com.srm.credit.controller;

import com.srm.credit.dto.PricingSimulationRequest;
import com.srm.credit.dto.PricingSimulationResponse;
import com.srm.credit.service.PricingSimulationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pricing/simulations")
@Tag(name = "Simulação")
@SecurityRequirement(name = "sessionAuth")
public class PricingSimulationController {
  private final PricingSimulationService service;

  public PricingSimulationController(PricingSimulationService service) {
    this.service = service;
  }

  @PostMapping
  @Operation(
      summary = "Simular preço indicativo sem cadastrar recebível ou criar liquidação",
      description =
          "Prazo teto(dias/30), operação em America/Sao_Paulo, half-even. USD usa a última cotação local registrada e vigente com até 24 horas; BRL independe de câmbio. Não chama o provedor externo. Resposta inclui parâmetros para reprodução; confirmação futura deverá recalcular.")
  @ApiResponse(responseCode = "200", description = "Simulação indicativa, sem gravação financeira.")
  @ApiResponse(
      responseCode = "400",
      description = "Entrada inválida ou vencimento inelegível.",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "401",
      description = "Autenticação necessária.",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "403",
      description = "Token CSRF inválido ou ausente.",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "503",
      description = "FX_RATE_UNAVAILABLE em USD: cotação ausente ou expirada.",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  public PricingSimulationResponse simulate(@Valid @RequestBody PricingSimulationRequest request) {
    return service.simulate(request);
  }
}
