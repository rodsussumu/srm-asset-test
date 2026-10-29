package com.srm.credit.config;

import com.srm.credit.exception.ErrorResponses;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
  @Bean
  InMemoryUserDetailsManager users(
      @Value("${spring.security.user.name}") String operator,
      @Value("${spring.security.user.password}") String operatorPassword,
      @Value("${app.admin.username}") String admin,
      @Value("${app.admin.password}") String adminPassword) {
    if (operator.isBlank()
        || admin.isBlank()
        || operator.equals(admin)
        || operatorPassword.length() < 12
        || adminPassword.length() < 12) {
      throw new IllegalArgumentException(
          "Configure usuários distintos e senhas de pelo menos 12 caracteres.");
    }
    var encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    return new InMemoryUserDetailsManager(
        User.withUsername(operator)
            .password(encoder.encode(operatorPassword))
            .roles("OPERATOR")
            .build(),
        User.withUsername(admin)
            .password(encoder.encode(adminPassword))
            .roles("ADMIN", "OPERATOR")
            .build());
  }

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http, ErrorResponses errors)
      throws Exception {
    AuthenticationEntryPoint unauthorized =
        (request, response, exception) ->
            errors.write(
                HttpStatus.UNAUTHORIZED,
                "AUTHENTICATION_REQUIRED",
                "Autenticação necessária.",
                request,
                response);
    return http.authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/actuator/health", "/actuator/health/**")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/login")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/fx-rates", "/api/v1/fx-rates/sync")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .hasAnyRole("OPERATOR", "ADMIN"))
        .requestCache(cache -> cache.disable())
        .formLogin(
            form ->
                form.loginPage("/api/v1/auth/me")
                    .loginProcessingUrl("/api/v1/auth/login")
                    .successHandler((request, response, authentication) -> response.setStatus(204))
                    .failureHandler(
                        (request, response, exception) ->
                            errors.write(
                                HttpStatus.UNAUTHORIZED,
                                "INVALID_CREDENTIALS",
                                "Usuário ou senha inválidos.",
                                request,
                                response)))
        .logout(
            logout ->
                logout
                    .logoutUrl("/api/v1/auth/logout")
                    .deleteCookies("JSESSIONID")
                    .logoutSuccessHandler(
                        (request, response, authentication) -> response.setStatus(204)))
        .exceptionHandling(
            handler ->
                handler
                    .authenticationEntryPoint(unauthorized)
                    .accessDeniedHandler(
                        (request, response, exception) -> {
                          var authentication =
                              SecurityContextHolder.getContext().getAuthentication();
                          if ((authentication == null
                                  || authentication instanceof AnonymousAuthenticationToken)
                              && !request.getRequestURI().equals("/api/v1/auth/login")) {
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
