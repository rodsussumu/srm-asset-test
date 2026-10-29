package com.srm.credit.controller;

import com.srm.credit.dto.CreateFxRateRequest;
import com.srm.credit.dto.FxRateResponse;
import com.srm.credit.dto.PageResponse;
import com.srm.credit.service.FxRateService;
import com.srm.credit.service.FxSynchronizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fx-rates")
@Tag(name = "Câmbio")
@SecurityRequirement(name = "sessionAuth")
public class FxRateController {
  private final FxRateService service;
  private final FxSynchronizationService synchronization;

  public FxRateController(FxRateService service, FxSynchronizationService synchronization) {
    this.service = service;
    this.synchronization = synchronization;
  }

  @PostMapping("/sync")
  @Operation(
      summary = "Sincronizar provedor mockado (ADMIN)",
      description =
          "Duas tentativas totais, até 1 segundo por resposta completa e intervalo de 100 ms. Retry somente em timeout, conexão ou 5xx; sem retry em 4xx/payload inválido. Mesma vigência/valor retorna o registro original.")
  @ApiResponse(responseCode = "200", description = "Cotação já existente com mesmo valor.")
  @ApiResponse(
      responseCode = "201",
      description = "Nova cotação criada; Location aponta para o recurso.")
  @ApiResponse(
      responseCode = "403",
      description = "Requer ADMIN e CSRF válido.",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "Vigência existente com outro valor.",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "503",
      description = "FX_PROVIDER_UNAVAILABLE: provedor falhou; histórico preservado.",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  public ResponseEntity<FxRateResponse> synchronize() {
    var result = synchronization.synchronize();
    var response = FxRateResponse.from(result.rate());
    return result.created()
        ? ResponseEntity.created(URI.create("/api/v1/fx-rates/" + response.id())).body(response)
        : ResponseEntity.ok(response);
  }

  @PostMapping
  @Operation(summary = "Registrar cotação BRL por USD (ADMIN)")
  @ApiResponse(responseCode = "201", description = "Cotação criada; histórico preservado.")
  @ApiResponse(
      responseCode = "400",
      description = "Cotação ou vigência inválida.",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "403",
      description = "Requer ADMIN e CSRF válido.",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "Vigência já registrada.",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  public ResponseEntity<FxRateResponse> create(@Valid @RequestBody CreateFxRateRequest request) {
    var response = FxRateResponse.from(service.create(request));
    return ResponseEntity.created(URI.create("/api/v1/fx-rates/" + response.id())).body(response);
  }

  @GetMapping("/{id}")
  @Operation(summary = "Consultar cotação histórica por identificador")
  public FxRateResponse find(@PathVariable UUID id) {
    return FxRateResponse.from(service.find(id));
  }

  @GetMapping("/current")
  @Operation(summary = "Consultar última cotação registrada e vigente, com idade de até 24 horas")
  @ApiResponse(responseCode = "200", description = "Cotação local vigente e válida.")
  @ApiResponse(
      responseCode = "503",
      description = "FX_RATE_UNAVAILABLE: cotação ausente ou com mais de 24 horas.",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  public FxRateResponse current() {
    return FxRateResponse.from(service.current());
  }

  @GetMapping
  @Operation(summary = "Histórico por vigência e id decrescentes, incluindo cotações futuras")
  public PageResponse<FxRateResponse> list(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return PageResponse.from(service.list(page, size), FxRateResponse::from);
  }
}
