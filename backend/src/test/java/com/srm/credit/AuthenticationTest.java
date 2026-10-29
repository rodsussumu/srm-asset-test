package com.srm.credit;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.srm.credit.config.CorrelationFilter;
import com.srm.credit.config.SecurityConfig;
import com.srm.credit.controller.AuthController;
import com.srm.credit.exception.ErrorResponses;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(
    controllers = AuthController.class,
    properties = {
      "spring.security.user.name=operator", "spring.security.user.password=operator-test-password",
      "app.admin.username=admin", "app.admin.password=admin-test-password"
    })
@Import({SecurityConfig.class, ErrorResponses.class, CorrelationFilter.class})
class AuthenticationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;

  private String token(MvcResult result) throws Exception {
    return mapper.readTree(result.getResponse().getContentAsString()).path("token").asText();
  }

  @Test
  void rotatesSessionAndTokenThenInvalidatesOnLogout() throws Exception {
    var first = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
    var session = (MockHttpSession) first.getRequest().getSession();
    var oldId = session.getId();
    var before = token(first);
    mvc.perform(
            post("/api/v1/auth/login")
                .session(session)
                .header("X-CSRF-TOKEN", before)
                .param("username", "operator")
                .param("password", "operator-test-password"))
        .andExpect(status().isNoContent());
    assertThat(session.getId()).isNotEqualTo(oldId);
    mvc.perform(get("/api/v1/auth/me").session(session))
        .andExpect(jsonPath("$.username").value("operator"))
        .andExpect(jsonPath("$.roles[0]").value("OPERATOR"));
    mvc.perform(post("/api/v1/assignors").session(session).header("X-CSRF-TOKEN", before))
        .andExpect(status().isForbidden());
    var refreshed = mvc.perform(get("/api/v1/auth/csrf").session(session)).andReturn();
    mvc.perform(
            post("/api/v1/auth/logout").session(session).header("X-CSRF-TOKEN", token(refreshed)))
        .andExpect(status().isNoContent())
        .andExpect(cookie().maxAge("JSESSIONID", 0));
    assertThat(session.isInvalid()).isTrue();
    mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
    var next = mvc.perform(get("/api/v1/auth/csrf")).andReturn();
    var nextSession = (MockHttpSession) next.getRequest().getSession();
    mvc.perform(
            post("/api/v1/auth/login")
                .session(nextSession)
                .header("X-CSRF-TOKEN", token(refreshed))
                .param("username", "operator")
                .param("password", "operator-test-password"))
        .andExpect(status().isForbidden());
  }

  @Test
  void rejectsMissingOrInvalidCsrfAtLoginAndLogout() throws Exception {
    mvc.perform(
            post("/api/v1/auth/login")
                .param("username", "operator")
                .param("password", "operator-test-password"))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/auth/login").with(csrf().useInvalidToken()))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/auth/logout").with(user("operator").roles("OPERATOR")))
        .andExpect(status().isForbidden());
  }

  @Test
  void rejectsBadCredentialsAndDoesNotRedirectOrExposeDetails() throws Exception {
    mvc.perform(
            post("/api/v1/auth/login")
                .with(csrf())
                .param("username", "operator")
                .param("password", "wrong"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
        .andExpect(jsonPath("$.correlationId").exists())
        .andExpect(header().doesNotExist("Location"));
  }

  @Test
  void adminAlsoHasOperatorRole() throws Exception {
    var result =
        mvc.perform(
                post("/api/v1/auth/login")
                    .with(csrf())
                    .param("username", "admin")
                    .param("password", "admin-test-password"))
            .andExpect(status().isNoContent())
            .andReturn();
    mvc.perform(get("/api/v1/auth/me").session((MockHttpSession) result.getRequest().getSession()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roles[0]").value("ADMIN"))
        .andExpect(jsonPath("$.roles[1]").value("OPERATOR"));
  }

  @Test
  void deniesAnonymousAndUnrecognizedRolesAndBasicAuth() throws Exception {
    for (String path :
        new String[] {
          "/api/v1/auth/me",
          "/api/v1/receivables",
          "/api/v1/fx-rates",
          "/api/v1/settlements",
          "/v3/api-docs",
          "/actuator/metrics"
        }) {
      mvc.perform(get(path)).andExpect(status().isUnauthorized());
    }
    mvc.perform(get("/api/v1/auth/me").with(user("guest").roles("GUEST")))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/auth/me").with(httpBasic("operator", "operator-test-password")))
        .andExpect(status().isUnauthorized());
    mvc.perform(post("/api/v1/fx-rates/sync").with(user("operator").roles("OPERATOR")).with(csrf()))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/assignors")
                .with(user("operator").roles("OPERATOR"))
                .with(csrf().useInvalidToken()))
        .andExpect(status().isForbidden());
  }
}
