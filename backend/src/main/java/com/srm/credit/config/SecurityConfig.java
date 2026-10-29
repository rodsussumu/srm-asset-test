package com.srm.credit.config;

import com.srm.credit.exception.ErrorResponses;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http, ErrorResponses errors)
      throws Exception {
    AuthenticationEntryPoint unauthorized =
        (request, response, exception) -> {
          response.setHeader("WWW-Authenticate", "Basic realm=\"srm\"");
          errors.write(
              HttpStatus.UNAUTHORIZED,
              "AUTHENTICATION_REQUIRED",
              "Autenticação necessária.",
              request,
              response);
        };
    return http.authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/actuator/health", "/actuator/health/**")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/fx-rates", "/api/v1/fx-rates/sync")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .authenticated())
        .httpBasic(basic -> basic.authenticationEntryPoint(unauthorized))
        .formLogin(form -> form.permitAll())
        .exceptionHandling(
            handler ->
                handler
                    .authenticationEntryPoint(unauthorized)
                    .accessDeniedHandler(
                        (request, response, exception) -> {
                          var authentication =
                              SecurityContextHolder.getContext().getAuthentication();
                          if (authentication == null
                              || authentication instanceof AnonymousAuthenticationToken) {
                            unauthorized.commence(request, response, null);
                          } else {
                            errors.write(
                                HttpStatus.FORBIDDEN,
                                "ACCESS_DENIED",
                                "Acesso negado ou token CSRF inválido.",
                                request,
                                response);
                          }
                        }))
        .build();
  }
}
