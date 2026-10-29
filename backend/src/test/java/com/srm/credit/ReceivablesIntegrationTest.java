package com.srm.credit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.srm.credit.model.Assignor;
import com.srm.credit.model.PaymentCurrency;
import com.srm.credit.model.Receivable;
import com.srm.credit.model.ReceivableStatus;
import com.srm.credit.model.ReceivableType;
import com.srm.credit.repository.AssignorRepository;
import com.srm.credit.repository.ReceivableRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.LockModeType;
import jakarta.persistence.RollbackException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@WithMockUser(username = "operator", roles = "OPERATOR")
@Import({
  ReceivablesIntegrationTest.FixedClockConfig.class,
  ReceivablesIntegrationTest.ErrorProbe.class
})
class ReceivablesIntegrationTest {
  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.11-alpine");

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.flyway.user", POSTGRES::getUsername);
    registry.add("spring.flyway.password", POSTGRES::getPassword);
    registry.add("spring.security.user.password", () -> "integration-test-only");
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class FixedClockConfig {
    @Bean
    @Primary
    Clock fixedClock() {
      return Clock.fixed(Instant.parse("2026-10-28T15:00:00Z"), ZoneOffset.UTC);
    }
  }

  @Autowired AssignorRepository assignors;
  @Autowired ReceivableRepository receivables;
  @Autowired JdbcTemplate jdbc;
  @Autowired Flyway flyway;
  @Autowired EntityManagerFactory entityManagerFactory;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;

  @RestController
  static class ErrorProbe {
    @GetMapping("/_test/failure")
    String fail() {
      throw new IllegalStateException("SELECT password FROM secret_table; hidden-internal-detail");
    }
  }

  @BeforeEach
  void cleanDatabase() {
    jdbc.update("DELETE FROM receivables");
    jdbc.update("DELETE FROM assignors");
  }

  @Test
  void migratesAnEmptyPostgresAndPersistsExactMoneyAndInitialVersion() {
    assertThat(flyway.info().applied()).hasSize(1);
    var assignor = assignors.saveAndFlush(new Assignor("Cedente de teste", Instant.now()));
    var receivable =
        receivables.saveAndFlush(
            new Receivable(
                assignor,
                new BigDecimal("9999999999999999.99"),
                LocalDate.of(2026, 11, 27),
                ReceivableType.DUPLICATA,
                PaymentCurrency.USD,
                Instant.now()));
    var reloaded = receivables.findById(receivable.getId()).orElseThrow();
    assertThat(reloaded.getFaceValue()).isEqualTo(new BigDecimal("9999999999999999.99"));
    assertThat(reloaded.getStatus()).isEqualTo(ReceivableStatus.AVAILABLE);
    assertThat(reloaded.getVersion()).isZero();
    assertThat(reloaded.getAssignor().getId()).isEqualTo(assignor.getId());
  }

  @Test
  void databaseRejectsInvalidMoneyEnumsAndForeignKeys() {
    var assignor = assignors.saveAndFlush(new Assignor("Constraints", Instant.now()));
    assertThatThrownBy(() -> insertRaw(assignor.getId(), "0.00", "DUPLICATA", "BRL"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> insertRaw(assignor.getId(), "1.00", "UNKNOWN", "BRL"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> insertRaw(assignor.getId(), "1.00", "CHEQUE", "EUR"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> insertRaw(UUID.randomUUID(), "1.00", "CHEQUE", "BRL"))
        .isInstanceOf(DataIntegrityViolationException.class);
    insertRaw(assignor.getId(), "1.00", "CHEQUE", "BRL");
    assertThatThrownBy(() -> jdbc.update("DELETE FROM assignors WHERE id = ?", assignor.getId()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void versionDetectsAStaleEntityInAnIndependentTransaction() {
    var assignor = assignors.saveAndFlush(new Assignor("Version", Instant.now()));
    var receivable =
        receivables.saveAndFlush(
            new Receivable(
                assignor,
                new BigDecimal("100.00"),
                LocalDate.of(2026, 11, 27),
                ReceivableType.CHEQUE,
                PaymentCurrency.BRL,
                Instant.now()));
    EntityManager em = entityManagerFactory.createEntityManager();
    try {
      em.getTransaction().begin();
      Receivable stale = em.find(Receivable.class, receivable.getId());
      jdbc.update("UPDATE receivables SET version = version + 1 WHERE id = ?", receivable.getId());
      em.lock(stale, LockModeType.OPTIMISTIC);
      assertThatThrownBy(() -> em.getTransaction().commit()).isInstanceOf(RollbackException.class);
    } finally {
      if (em.getTransaction().isActive()) {
        em.getTransaction().rollback();
      }
      em.close();
    }
    assertThat(receivables.findById(receivable.getId()).orElseThrow().getVersion()).isEqualTo(1L);
  }

  @Test
  void createsAndReadsResourcesWithoutExposingJpaEntities() throws Exception {
    var assignorId = createAssignor("  Cedente API  ");
    mvc.perform(get("/api/v1/assignors/" + assignorId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Cedente API"));
    var created =
        mvc.perform(
                post("/api/v1/receivables")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(receivableRequest(assignorId, "100000.00").toString()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.faceValue").value("100000.00"))
            .andExpect(jsonPath("$.faceCurrency").value("BRL"))
            .andExpect(jsonPath("$.status").value("AVAILABLE"))
            .andExpect(jsonPath("$.version").value(0))
            .andExpect(jsonPath("$.createdAt").value("2026-10-28T15:00:00Z"))
            .andExpect(jsonPath("$.assignor").doesNotExist())
            .andReturn();
    String id = mapper.readTree(created.getResponse().getContentAsString()).get("id").asText();
    assertThat(created.getResponse().getHeader("Location")).isEqualTo("/api/v1/receivables/" + id);
    mvc.perform(get("/api/v1/receivables/" + id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.assignorId").value(assignorId.toString()))
        .andExpect(jsonPath("$.faceValue").isString());
    assertThat(receivables.count()).isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-1", "1.005", "10000000000000000.00", "1e3"})
  void apiRejectsInvalidMoneyWithoutWriting(String face) throws Exception {
    var assignorId = createAssignor("Invalid amounts");
    mvc.perform(
            post("/api/v1/receivables")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(receivableRequest(assignorId, face).toString()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
        .andExpect(jsonPath("$.errors.faceValue").exists());
    assertThat(receivables.count()).isZero();
  }

  @Test
  void apiRejectsJsonNumbersInsteadOfSilentlyCoercingMoneyToStrings() throws Exception {
    var request = receivableRequest(createAssignor("Strict JSON"), "100.00");
    request.put("faceValue", new BigDecimal("100.00"));
    mvc.perform(
            post("/api/v1/receivables")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(request.toString()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.faceValue").exists());
    assertThat(receivables.count()).isZero();
  }

  @ParameterizedTest
  @ValueSource(strings = {"type", "paymentCurrency"})
  void apiRejectsUnknownAndNumericEnums(String field) throws Exception {
    var request = receivableRequest(createAssignor("Enum validation"), "100.00");
    request.put(field, "UNKNOWN");
    mvc.perform(
            post("/api/v1/receivables")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(request.toString()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors." + field).exists());
    request.put(field, 0);
    mvc.perform(
            post("/api/v1/receivables")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(request.toString()))
        .andExpect(status().isBadRequest());
    assertThat(receivables.count()).isZero();
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 0, 1081})
  void apiRejectsIneligibleDates(int days) throws Exception {
    var request = receivableRequest(createAssignor("Due date"), "100.00");
    request.put("dueDate", LocalDate.of(2026, 10, 28).plusDays(days).toString());
    mvc.perform(
            post("/api/v1/receivables")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(request.toString()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.dueDate").exists());
    assertThat(receivables.count()).isZero();
  }

  @Test
  void rejectsMissingFieldsInvalidNamesAndUnknownAssignors() throws Exception {
    mvc.perform(
            post("/api/v1/assignors")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\" \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.name").exists());
    mvc.perform(
            post("/api/v1/receivables")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.assignorId").exists());
    mvc.perform(
            post("/api/v1/receivables")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(receivableRequest(UUID.randomUUID(), "100.00").toString()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.assignorId").exists());
    assertThat(assignors.count()).isZero();
    assertThat(receivables.count()).isZero();
  }

  @Test
  void paginatesAndFiltersOnTheServer() throws Exception {
    var assignorId = createAssignor("Pages");
    createAssignor("Second assignor");
    for (String face : new String[] {"100.00", "200.00"}) {
      mvc.perform(
              post("/api/v1/receivables")
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(receivableRequest(assignorId, face).toString()))
          .andExpect(status().isCreated());
    }
    var first =
        mvc.perform(get("/api/v1/receivables?page=0&size=1&status=AVAILABLE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.total").value(2))
            .andReturn();
    var second =
        mvc.perform(get("/api/v1/receivables?page=1&size=1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(1))
            .andReturn();
    String firstId =
        mapper
            .readTree(first.getResponse().getContentAsString())
            .get("items")
            .get(0)
            .get("id")
            .asText();
    String secondId =
        mapper
            .readTree(second.getResponse().getContentAsString())
            .get("items")
            .get(0)
            .get("id")
            .asText();
    assertThat(firstId).isGreaterThan(secondId);
    mvc.perform(get("/api/v1/receivables?status=SETTLED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty())
        .andExpect(jsonPath("$.total").value(0));
    mvc.perform(get("/api/v1/assignors?size=1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.total").value(2));
    mvc.perform(get("/api/v1/receivables?page=20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty())
        .andExpect(jsonPath("$.total").value(2));
  }

  @ParameterizedTest
  @ValueSource(strings = {"page=-1", "size=0", "size=101", "status=UNKNOWN", "page=no"})
  void rejectsInvalidQueryParameters(String query) throws Exception {
    mvc.perform(get("/api/v1/receivables?" + query))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
  }

  @Test
  void distinguishesMissingResourcesFromInvalidIdentifiers() throws Exception {
    mvc.perform(get("/api/v1/assignors/" + UUID.randomUUID())).andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/receivables/" + UUID.randomUUID())).andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/receivables/not-a-uuid")).andExpect(status().isBadRequest());
    mvc.perform(put("/api/v1/receivables/" + UUID.randomUUID()).with(csrf()))
        .andExpect(status().isMethodNotAllowed());
  }

  @Test
  void preservesValidCorrelationAndReplacesInvalidHeaders() throws Exception {
    String correlationId = UUID.randomUUID().toString();
    mvc.perform(get("/api/v1/receivables/not-a-uuid").header("X-Correlation-ID", correlationId))
        .andExpect(status().isBadRequest())
        .andExpect(header().string("X-Correlation-ID", correlationId))
        .andExpect(jsonPath("$.correlationId").value(correlationId));
    var result =
        mvc.perform(get("/api/v1/receivables").header("X-Correlation-ID", "untrusted-log-value"))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(UUID.fromString(result.getResponse().getHeader("X-Correlation-ID"))).isNotNull();
  }

  @Test
  void unexpectedErrorsHaveSafeDetailsAndCorrelation() throws Exception {
    String correlationId = UUID.randomUUID().toString();
    var result =
        mvc.perform(get("/_test/failure").header("X-Correlation-ID", correlationId))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(jsonPath("$.correlationId").value(correlationId))
            .andReturn();
    assertThat(result.getResponse().getContentAsString())
        .doesNotContain(
            "SELECT",
            "password",
            "secret_table",
            "IllegalStateException",
            "hidden-internal-detail");
  }

  @Test
  @WithAnonymousUser
  void requiresAuthenticationAndKeepsHealthPublic() throws Exception {
    mvc.perform(get("/api/v1/receivables"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
        .andExpect(jsonPath("$.correlationId").exists());
    mvc.perform(get("/actuator/health")).andExpect(status().isOk());
  }

  @Test
  void rejectsMutationsWithoutCsrf() throws Exception {
    mvc.perform(
            post("/api/v1/assignors")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Denied\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    assertThat(assignors.count()).isZero();
  }

  @Test
  void publishesTypedOpenApiForImplementedEndpoints() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/assignors'].post.responses['201']").exists())
        .andExpect(jsonPath("$.paths['/api/v1/receivables'].post.responses['201']").exists())
        .andExpect(
            jsonPath("$.components.schemas.CreateReceivableRequest.properties.faceValue.type")
                .value("string"));
  }

  private UUID createAssignor(String name) throws Exception {
    var body = mapper.createObjectNode().put("name", name);
    var result =
        mvc.perform(
                post("/api/v1/assignors")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body.toString()))
            .andExpect(status().isCreated())
            .andReturn();
    String id = mapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    assertThat(result.getResponse().getHeader("Location")).isEqualTo("/api/v1/assignors/" + id);
    return UUID.fromString(id);
  }

  private ObjectNode receivableRequest(UUID assignorId, String faceValue) {
    return mapper
        .createObjectNode()
        .put("assignorId", assignorId.toString())
        .put("faceValue", faceValue)
        .put("dueDate", "2026-11-27")
        .put("type", "DUPLICATA")
        .put("paymentCurrency", "BRL");
  }

  private void insertRaw(UUID assignorId, String face, String type, String currency) {
    jdbc.update(
        """
        INSERT INTO receivables (id, assignor_id, face_value, due_date, type, payment_currency, created_at)
        VALUES (?, ?, ?, DATE '2026-11-27', ?, ?, CURRENT_TIMESTAMP)
        """,
        UUID.randomUUID(),
        assignorId,
        new BigDecimal(face),
        type,
        currency);
  }
}
