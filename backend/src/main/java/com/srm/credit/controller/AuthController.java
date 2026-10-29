package com.srm.credit.controller;

import com.srm.credit.dto.SessionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Sessão")
public class AuthController {
  public record CsrfResponse(String headerName, String token) {}

  @GetMapping("/csrf")
  @Operation(summary = "Obter token CSRF da sessão; renovar após login/logout")
  public CsrfResponse csrf(CsrfToken token) {
    return new CsrfResponse(token.getHeaderName(), token.getToken());
  }

  @GetMapping("/me")
  @SecurityRequirement(name = "sessionAuth")
  @Operation(summary = "Consultar identidade autenticada e papéis")
  public SessionResponse me(Authentication authentication) {
    return new SessionResponse(
        authentication.getName(),
        authentication.getAuthorities().stream()
            .map(authority -> authority.getAuthority().replaceFirst("^ROLE_", ""))
            .sorted()
            .toList());
  }
}
