package com.srm.credit.controller;

import com.srm.credit.dto.CreateSettlementRequest;
import com.srm.credit.dto.SettlementResponse;
import com.srm.credit.dto.StatementResponse;
import com.srm.credit.model.PaymentCurrency;
import com.srm.credit.model.StatementQuery;
import com.srm.credit.service.SettlementService;
import com.srm.credit.service.StatementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settlements")
@Tag(name = "Liquidação")
@SecurityRequirement(name = "basicAuth")
public class SettlementController {
  private final SettlementService service;
  private final StatementService statements;

  public SettlementController(SettlementService service, StatementService statements) {
    this.service = service;
    this.statements = statements;
  }

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Consultar extrato histórico com período UTC e filtros opcionais",
      description =
          "Instante de liquidação no intervalo [from,to), de até 366 dias. Ordenação settledAt/id decrescente; página zero, tamanho padrão 20 e máximo 100. Totais de pagamento e contagem por moeda abrangem todo o filtro, independentes da página; moedas sem registros são omitidas. Lê snapshots sem recálculo. Cada resposta usa snapshot REPEATABLE READ; páginas de requisições distintas podem mudar com novas confirmações.")
  @ApiResponse(
      responseCode = "400",
      description = "Período, filtro ou paginação inválidos.",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "200",
      description = "Itens paginados e contagem/totais do filtro no mesmo snapshot.",
      content =
          @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = StatementResponse.class)))
  @ApiResponse(
      responseCode = "401",
      description = "Autenticação necessária.",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  public StatementResponse list(
      @RequestParam(required = false)
          @Parameter(
              required = true,
              description = "Início inclusivo ISO-8601 com fuso, normalizado para UTC.")
          Instant from,
      @RequestParam(required = false)
          @Parameter(
              required = true,
              description = "Fim exclusivo; após from e até 366 dias de intervalo.")
          Instant to,
      @RequestParam(required = false) UUID assignorId,
      @RequestParam(required = false) PaymentCurrency currency,
      @RequestParam(defaultValue = "0")
          @Parameter(schema = @Schema(minimum = "0", defaultValue = "0"))
          int page,
      @RequestParam(defaultValue = "20")
          @Parameter(schema = @Schema(minimum = "1", maximum = "100", defaultValue = "20"))
          int size) {
    return statements.find(new StatementQuery(from, to, assignorId, currency, page, size));
  }

  @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Confirmar aquisição integral com recálculo e idempotência",
      description =
          "Idempotency-Key UUID por operador/rota. Pedido normalizado: valor com duas casas. Retry retorna 201/corpo/Location originais antes de recálculo; retenção sem expiração. Espera pela mesma chave limitada a 3 segundos. Requer CSRF. Commit de estado, versão, auditoria e resposta antes do sucesso. Não executa transferência bancária.")
  @ApiResponse(
      responseCode = "201",
      description = "Liquidação confirmada ou replay exato.",
      content =
          @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = SettlementResponse.class)))
  @ApiResponse(
      responseCode = "400",
      description = "Chave, entrada ou moeda inválida; vencimento inelegível.",
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
      responseCode = "404",
      description = "Recebível não encontrado.",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description =
          "PRICE_CHANGED, IDEMPOTENCY_KEY_REUSED, RECEIVABLE_ALREADY_SETTLED ou REQUEST_IN_PROGRESS (Retry-After: 1).",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "503",
      description = "FX_RATE_UNAVAILABLE: sem cotação local válida para USD.",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "500",
      description = "Falha inesperada; nenhuma confirmação parcial.",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  public ResponseEntity<String> create(
      @Valid @RequestBody CreateSettlementRequest body,
      @RequestHeader(name = "Idempotency-Key", required = false)
          @Parameter(
              required = true,
              description = "UUID por intenção, escopado ao operador e à rota.",
              schema = @Schema(type = "string", format = "uuid"))
          String key,
      Authentication authentication,
      HttpServletRequest request) {
    var result =
        service.confirm(
            authentication.getName(),
            key,
            body,
            UUID.fromString((String) request.getAttribute("correlationId")));
    return ResponseEntity.status(result.statusCode())
        .location(URI.create(result.location()))
        .contentType(MediaType.APPLICATION_JSON)
        .body(result.body());
  }

  @GetMapping("/{id}")
  @Operation(summary = "Consultar snapshot original da liquidação, sem recálculo")
  @ApiResponse(
      responseCode = "200",
      description = "Snapshot original da liquidação.",
      content =
          @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = SettlementResponse.class)))
  @ApiResponse(
      responseCode = "404",
      description = "Liquidação não encontrada.",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  public SettlementResponse find(@PathVariable UUID id) {
    return SettlementResponse.from(service.find(id));
  }
}
