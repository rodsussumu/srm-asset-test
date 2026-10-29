package com.srm.credit.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info =
        @Info(
            title = "SRM Credit Engine",
            version = "v1",
            description =
                "Cadastro de cedentes/recebíveis, histórico de câmbio, sincronização e simulação indicativa. Valores e taxas são strings decimais."))
@SecurityScheme(
    name = "sessionAuth",
    type = SecuritySchemeType.APIKEY,
    in = SecuritySchemeIn.COOKIE,
    paramName = "JSESSIONID")
public class ApiConfig {
  @Bean
  OpenApiCustomizer sessionEndpoints() {
    return api -> {
      var credentials =
          new ObjectSchema()
              .addProperty("username", new StringSchema())
              .addProperty("password", new StringSchema().format("password"))
              .required(List.of("username", "password"));
      var login =
          new Operation()
              .summary("Autenticar e renovar sessão/CSRF")
              .tags(List.of("Sessão"))
              .requestBody(
                  new RequestBody()
                      .required(true)
                      .content(
                          new Content()
                              .addMediaType(
                                  "application/x-www-form-urlencoded",
                                  new MediaType().schema(credentials))))
              .responses(
                  new ApiResponses()
                      .addApiResponse(
                          "204", new ApiResponse().description("Autenticado; obter novo CSRF."))
                      .addApiResponse(
                          "401", new ApiResponse().description("Credenciais inválidas."))
                      .addApiResponse("403", new ApiResponse().description("CSRF inválido.")));
      var logout =
          new Operation()
              .summary("Encerrar sessão e invalidar cookie/CSRF")
              .tags(List.of("Sessão"))
              .addSecurityItem(new SecurityRequirement().addList("sessionAuth"))
              .responses(
                  new ApiResponses()
                      .addApiResponse("204", new ApiResponse().description("Sessão encerrada."))
                      .addApiResponse("403", new ApiResponse().description("CSRF inválido.")));
      api.path("/api/v1/auth/login", new PathItem().post(login));
      api.path("/api/v1/auth/logout", new PathItem().post(logout));
      api.getPaths()
          .values()
          .forEach(
              path -> {
                if (path.getPost() != null)
                  path.getPost()
                      .addParametersItem(
                          new HeaderParameter()
                              .name("X-CSRF-TOKEN")
                              .required(true)
                              .description(
                                  "Token obtido em /api/v1/auth/csrf; renovar após login/logout.")
                              .schema(new StringSchema()));
              });
    };
  }

  @Bean
  public Jackson2ObjectMapperBuilderCustomizer strictApiTypes() {
    return builder ->
        builder.postConfigurer(
            mapper -> {
              mapper.enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS);
              mapper
                  .coercionConfigFor(LogicalType.Textual)
                  .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                  .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                  .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
            });
  }
}
