package com.srm.credit.controller;

import com.srm.credit.dto.CreateReceivableRequest;
import com.srm.credit.dto.PageResponse;
import com.srm.credit.dto.ReceivableResponse;
import com.srm.credit.model.ReceivableStatus;
import com.srm.credit.service.ReceivableService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/receivables")
@Tag(name = "Recebíveis")
@SecurityRequirement(name = "basicAuth")
public class ReceivableController {
  private final ReceivableService service;

  public ReceivableController(ReceivableService service) {
    this.service = service;
  }

  @PostMapping
  @Operation(summary = "Cadastrar recebível em BRL com pagamento BRL ou USD")
  @ApiResponse(
      responseCode = "201",
      description = "Recebível criado no estado AVAILABLE e versão zero.")
  @ApiResponse(
      responseCode = "400",
      description = "Entrada inválida, vencimento inelegível ou cedente inexistente.")
  public ResponseEntity<ReceivableResponse> create(
      @Valid @RequestBody CreateReceivableRequest request) {
    var response =
        ReceivableResponse.from(
            service.create(
                request.assignorId(),
                request.faceValue(),
                request.dueDate(),
                request.type(),
                request.paymentCurrency()));
    return ResponseEntity.created(URI.create("/api/v1/receivables/" + response.id()))
        .body(response);
  }

  @GetMapping("/{id}")
  @Operation(summary = "Consultar recebível por identificador")
  @ApiResponse(responseCode = "404", description = "Recebível não encontrado.")
  public ReceivableResponse find(@PathVariable UUID id) {
    return ReceivableResponse.from(service.find(id));
  }

  @GetMapping
  @Operation(summary = "Listar recebíveis com paginação e filtro opcional por estado")
  public PageResponse<ReceivableResponse> list(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      @RequestParam(required = false) ReceivableStatus status) {
    return PageResponse.from(service.list(page, size, status), ReceivableResponse::from);
  }
}
