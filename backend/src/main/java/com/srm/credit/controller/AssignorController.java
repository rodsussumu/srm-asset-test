package com.srm.credit.controller;

import com.srm.credit.dto.AssignorResponse;
import com.srm.credit.dto.CreateAssignorRequest;
import com.srm.credit.dto.PageResponse;
import com.srm.credit.service.AssignorService;
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
@RequestMapping("/api/v1/assignors")
@Tag(name = "Cedentes")
@SecurityRequirement(name = "basicAuth")
public class AssignorController {
  private final AssignorService service;

  public AssignorController(AssignorService service) {
    this.service = service;
  }

  @PostMapping
  @Operation(summary = "Cadastrar cedente")
  @ApiResponse(
      responseCode = "201",
      description = "Cedente criado; Location aponta para o recurso.")
  @ApiResponse(responseCode = "400", description = "Entrada inválida.")
  public ResponseEntity<AssignorResponse> create(
      @Valid @RequestBody CreateAssignorRequest request) {
    var response = AssignorResponse.from(service.create(request.name()));
    return ResponseEntity.created(URI.create("/api/v1/assignors/" + response.id())).body(response);
  }

  @GetMapping("/{id}")
  @Operation(summary = "Consultar cedente por identificador")
  @ApiResponse(responseCode = "404", description = "Cedente não encontrado.")
  public AssignorResponse find(@PathVariable UUID id) {
    return AssignorResponse.from(service.find(id));
  }

  @GetMapping
  @Operation(summary = "Listar cedentes por criação decrescente, com desempate por id")
  public PageResponse<AssignorResponse> list(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return PageResponse.from(service.list(page, size), AssignorResponse::from);
  }
}
