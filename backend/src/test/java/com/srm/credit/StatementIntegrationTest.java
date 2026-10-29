package com.srm.credit;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.srm.credit.model.PaymentCurrency;
import com.srm.credit.model.ReceivableType;
import com.srm.credit.model.Settlement;
import com.srm.credit.repository.SettlementRepository;
import com.srm.credit.repository.StatementRepository;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@WithMockUser(roles = "OPERATOR")
class StatementIntegrationTest {
  static final Instant START = Instant.parse("2026-01-01T12:00:00Z");
  static final String RANGE =
      "/api/v1/settlements?from=2026-01-01T12:00:00Z&to=2026-01-02T12:00:00Z";

  @Container
  static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:17.11-alpine");

  static JdbcTemplate admin;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) throws Exception {
    PG.copyFileToContainer(
        MountableFile.forHostPath("../infra/postgres/bootstrap.sql"), "/tmp/bootstrap.sql");
    var result =
        PG.execInContainer(
            "env",
            "DATABASE_USER=srm_app",
            "DATABASE_PASSWORD=statement-test-only",
            "psql",
            "-X",
            "-U",
            PG.getUsername(),
            "-d",
            PG.getDatabaseName(),
            "-f",
            "/tmp/bootstrap.sql");
    assertThat(result.getExitCode()).as(result.getStderr()).isZero();
    admin =
        new JdbcTemplate(
            new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword()));
    registry.add("spring.datasource.url", PG::getJdbcUrl);
    registry.add("spring.datasource.username", () -> "srm_app");
    registry.add("spring.datasource.password", () -> "statement-test-only");
    registry.add("spring.flyway.user", PG::getUsername);
    registry.add("spring.flyway.password", PG::getPassword);
    registry.add("spring.security.user.password", () -> "integration-test-only");
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired JdbcTemplate runtime;
  @MockitoSpyBean StatementRepository statements;

  @BeforeEach
  void clean() {
    admin.execute(
        "TRUNCATE idempotency_records,settlements,receivables,assignors,fx_rates CASCADE");
  }

  @Test
  void selectsSemiOpenSettlementPeriodUsingRuntimeLogin() throws Exception {
    assertThat(runtime.queryForObject("SELECT current_user", String.class)).isEqualTo("srm_app");
    UUID assignor = UUID.randomUUID();
    UUID included = seed(assignor, START, "BRL", null, null);
    seed(assignor, START.minusNanos(1000), "BRL", null, null);
    seed(assignor, START.plusSeconds(86400), "BRL", null, null);
    mvc.perform(get(RANGE))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.items[0].id").value(included.toString()))
        .andExpect(jsonPath("$.items[0].settledAt").value(START.toString()))
        .andExpect(jsonPath("$.items[0].faceValue").value("100000.00"))
        .andExpect(jsonPath("$.items[0].presentValueBrl").value("92859.94"))
        .andExpect(jsonPath("$.items[0].discountBrl").value("7140.06"));
  }

  @Test
  void optionalFiltersCombineWithoutCrossingAssignorOrCurrency() throws Exception {
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    UUID selected = seed(first, START, "USD", null, null);
    seed(first, START, "BRL", null, null);
    seed(second, START, "USD", null, null);
    mvc.perform(get(RANGE + "&assignorId=" + first + "&currency=USD"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.items[0].id").value(selected.toString()));
    mvc.perform(get(RANGE + "&assignorId=" + first)).andExpect(jsonPath("$.total").value(2));
    mvc.perform(get(RANGE + "&currency=USD")).andExpect(jsonPath("$.total").value(2));
    mvc.perform(get(RANGE)).andExpect(jsonPath("$.total").value(3));
    mvc.perform(get(RANGE + "&assignorId=" + UUID.randomUUID()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty())
        .andExpect(jsonPath("$.total").value(0));
  }

  @Test
  void paginates45TiedTimestampsByPostgresUuidOrderAndSupportsEmptyPages() throws Exception {
    UUID assignor = UUID.randomUUID();
    for (int n = 1; n <= 45; n++) {
      seed(
          assignor,
          START,
          "BRL",
          null,
          UUID.fromString(String.format("00000000-0000-0000-0000-%012d", n)));
    }
    mvc.perform(get(RANGE))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.size").value(20))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.items.length()").value(20))
        .andExpect(jsonPath("$.items[0].id").value("00000000-0000-0000-0000-000000000045"));
    mvc.perform(get(RANGE + "&page=1"))
        .andExpect(jsonPath("$.total").value(45))
        .andExpect(jsonPath("$.items.length()").value(20))
        .andExpect(jsonPath("$.items[0].id").value("00000000-0000-0000-0000-000000000025"))
        .andExpect(jsonPath("$.items[19].id").value("00000000-0000-0000-0000-000000000006"));
    mvc.perform(get(RANGE + "&page=3"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty())
        .andExpect(jsonPath("$.total").value(45));
    mvc.perform(get(RANGE + "&page=2147483647&size=100"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty())
        .andExpect(jsonPath("$.total").value(45));
  }

  @Test
  void sortsBySettlementTimeBeforeUuidAndPreservesSubMicrosecondBoundaries() throws Exception {
    UUID assignor = UUID.randomUUID();
    seed(assignor, START, "BRL", null, new UUID(-1L, -1L));
    UUID newer = seed(assignor, START.plusNanos(1000), "BRL", null, new UUID(0, 1));
    mvc.perform(get(RANGE)).andExpect(jsonPath("$.items[0].id").value(newer.toString()));
    mvc.perform(
            get("/api/v1/settlements")
                .param("from", START.plusNanos(1).toString())
                .param("to", START.plusNanos(1001).toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.items[0].id").value(newer.toString()));
    mvc.perform(
            get("/api/v1/settlements")
                .param("from", START.toString())
                .param("to", START.plusNanos(1).toString()))
        .andExpect(jsonPath("$.total").value(1));
  }

  @Test
  void acceptsExactly366DaysAndEquivalentOffsetInstants() throws Exception {
    seed(UUID.randomUUID(), START, "BRL", null, null);
    mvc.perform(
            get("/api/v1/settlements")
                .param("from", START.toString())
                .param("to", START.plusSeconds(366L * 86400).toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1));
    mvc.perform(
            get("/api/v1/settlements")
                .param("from", "2026-01-01T09:00:00-03:00")
                .param("to", "2026-01-02T09:00:00-03:00"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "?from=2026-01-01T12:00:00Z",
        "?to=2026-01-02T12:00:00Z",
        "?from=2026-01-01T12:00:00Z&to=2026-01-01T12:00:00Z",
        "?from=2026-01-02T12:00:00Z&to=2026-01-01T12:00:00Z",
        "?from=2026-01-01T12:00:00Z&to=2027-01-02T12:00:00.000000001Z",
        "?from=invalid&to=2026-01-02T12:00:00Z"
      })
  void rejectsInvalidPeriodsWithSafeFieldErrors(String query) throws Exception {
    mvc.perform(get("/api/v1/settlements" + query))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
        .andExpect(jsonPath("$.errors").exists())
        .andExpect(jsonPath("$.correlationId").exists());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "page=-1",
        "size=0",
        "size=101",
        "size=no",
        "currency=EUR",
        "assignorId=invalid",
        "assignorId=1%27%20OR%201=1--"
      })
  void rejectsInvalidFiltersAndPaginationBeforeSql(String query) throws Exception {
    mvc.perform(get(RANGE + "&" + query))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
  }

  @Test
  @WithAnonymousUser
  void requiresAuthenticationForStatements() throws Exception {
    mvc.perform(get(RANGE)).andExpect(status().isUnauthorized());
  }

  @Test
  void totalsCoverTheWholeFilterAndKeepCurrenciesSeparateOnEveryPage() throws Exception {
    UUID assignor = UUID.randomUUID();
    UUID other = UUID.randomUUID();
    seed(assignor, START, "BRL", null, null);
    seed(assignor, START.plusSeconds(1), "USD", null, null);
    seed(assignor, START.plusSeconds(2), "USD", null, null);
    seed(other, START, "BRL", null, null);
    seed(assignor, START.plusSeconds(86400), "USD", null, null);
    for (int page : new int[] {0, 1, 10}) {
      mvc.perform(get(RANGE + "&assignorId=" + assignor + "&size=1&page=" + page))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.total").value(3))
          .andExpect(jsonPath("$.totalsByCurrency.length()").value(2))
          .andExpect(jsonPath("$.totalsByCurrency[0].currency").value("BRL"))
          .andExpect(jsonPath("$.totalsByCurrency[0].count").value(1))
          .andExpect(jsonPath("$.totalsByCurrency[0].paymentTotal").value("92859.94"))
          .andExpect(jsonPath("$.totalsByCurrency[1].currency").value("USD"))
          .andExpect(jsonPath("$.totalsByCurrency[1].count").value(2))
          .andExpect(jsonPath("$.totalsByCurrency[1].paymentTotal").value("34189.34"))
          .andExpect(jsonPath("$.paymentTotal").doesNotExist());
    }
    mvc.perform(get(RANGE + "&assignorId=" + assignor + "&currency=USD&size=1"))
        .andExpect(jsonPath("$.total").value(2))
        .andExpect(jsonPath("$.totalsByCurrency.length()").value(1))
        .andExpect(jsonPath("$.totalsByCurrency[0].currency").value("USD"))
        .andExpect(jsonPath("$.totalsByCurrency[0].paymentTotal").value("34189.34"));
    mvc.perform(get(RANGE + "&assignorId=" + UUID.randomUUID()))
        .andExpect(jsonPath("$.totalsByCurrency").isEmpty());
  }

  @Test
  void aggregatesExactStoredDecimalsWithoutUsingCurrentFxOrCurrentAssignorName() throws Exception {
    UUID assignor = UUID.randomUUID();
    seed(assignor, START, "USD", "0.10", null);
    seed(assignor, START, "USD", "0.20", null);
    admin.update(
        "INSERT INTO fx_rates VALUES (?, 'USD/BRL', 9, 'new-rate', ?, ?)",
        UUID.randomUUID(),
        Timestamp.from(START.plusSeconds(1)),
        Timestamp.from(START.plusSeconds(1)));
    admin.update("UPDATE assignors SET name='Changed name'");
    mvc.perform(get(RANGE))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalsByCurrency[0].paymentTotal").value("0.30"))
        .andExpect(jsonPath("$.items[0].assignorName").value("Original assignor"))
        .andExpect(jsonPath("$.items[0].fxRate.rate").value("5.43210000"));
  }

  @Test
  void totalsCanExceedAnIndividualStoredPaymentWithoutOverflowOrFloatConversion() throws Exception {
    UUID assignor = UUID.randomUUID();
    String maximum = "9999999999999999999999999999.99";
    seed(assignor, START, "USD", maximum, null);
    seed(assignor, START, "USD", maximum, null);
    mvc.perform(get(RANGE))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.totalsByCurrency[0].paymentTotal")
                .value("19999999999999999999999999999.98"));
  }

  @Test
  void pageCountAndTotalsShareOneSnapshotAcrossConcurrentInsertion() throws Exception {
    UUID assignor = UUID.randomUUID();
    seed(assignor, START, "BRL", null, null);
    var read = new java.util.concurrent.CountDownLatch(1);
    var release = new java.util.concurrent.CountDownLatch(1);
    var first = new java.util.concurrent.atomic.AtomicBoolean(true);
    org.mockito.Mockito.doAnswer(
            invocation -> {
              Object items = invocation.callRealMethod();
              if (first.getAndSet(false)) {
                assertThat(runtime.queryForObject("SHOW transaction_isolation", String.class))
                    .isEqualTo("repeatable read");
                assertThat(runtime.queryForObject("SHOW transaction_read_only", String.class))
                    .isEqualTo("on");
                read.countDown();
                assertThat(release.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
              }
              return items;
            })
        .when(statements)
        .list(org.mockito.ArgumentMatchers.any(com.srm.credit.model.StatementQuery.class));
    try (var threads = java.util.concurrent.Executors.newSingleThreadExecutor()) {
      try {
        var response =
            threads.submit(
                () ->
                    mvc.perform(
                            get(RANGE)
                                .with(
                                    org.springframework.security.test.web.servlet.request
                                        .SecurityMockMvcRequestPostProcessors.user("operator")
                                        .roles("OPERATOR")))
                        .andReturn());
        assertThat(read.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        seed(
            assignor,
            START.plusSeconds(1),
            "USD",
            null,
            null); // A separate connection commits while the page transaction is paused.
        release.countDown();
        var result = response.get(10, java.util.concurrent.TimeUnit.SECONDS);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        var json = mapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.get("items").size()).isEqualTo(1);
        assertThat(json.get("total").asLong()).isEqualTo(1);
        assertThat(json.get("totalsByCurrency").size()).isEqualTo(1);
        assertThat(json.get("totalsByCurrency").get(0).get("currency").asText()).isEqualTo("BRL");
      } finally {
        release.countDown();
      }
    }
    mvc.perform(get(RANGE))
        .andExpect(jsonPath("$.total").value(2))
        .andExpect(jsonPath("$.totalsByCurrency.length()").value(2));
  }

  UUID seed(UUID assignor, Instant at, String currency, String payment, UUID requestedId) {
    UUID id = requestedId == null ? UUID.randomUUID() : requestedId;
    UUID receivable = UUID.randomUUID();
    admin.update(
        "INSERT INTO assignors VALUES (?, 'Original assignor', ?) ON CONFLICT DO NOTHING",
        assignor,
        Timestamp.from(START));
    admin.update(
        "INSERT INTO receivables (id,assignor_id,face_value,due_date,type,payment_currency,status,version,created_at) VALUES (?,?,100000,DATE '2026-04-01','DUPLICATA',?,'SETTLED',1,?)",
        receivable,
        assignor,
        currency,
        Timestamp.from(START));
    UUID fx = null;
    if (currency.equals("USD")) {
      fx = UUID.randomUUID();
      admin.update(
          "INSERT INTO fx_rates VALUES (?, 'USD/BRL', 5.4321, 'historic', ?, ?) ON CONFLICT DO NOTHING",
          fx,
          Timestamp.from(at.minusSeconds(3600)),
          Timestamp.from(at.minusSeconds(3600)));
      fx =
          admin.queryForObject(
              "SELECT id FROM fx_rates WHERE pair='USD/BRL' AND effective_at=?",
              UUID.class,
              Timestamp.from(at.minusSeconds(3600)));
    }
    new SettlementRepository(admin)
        .save(
            new Settlement(
                id,
                receivable,
                assignor,
                "Original assignor",
                "operator",
                new BigDecimal("100000.00"),
                LocalDate.of(2026, 4, 1),
                ReceivableType.DUPLICATA,
                3,
                LocalDate.of(2026, 1, 1),
                at,
                new BigDecimal("0.0100"),
                new BigDecimal("0.0150"),
                new BigDecimal("92859.94"),
                new BigDecimal("7140.06"),
                PaymentCurrency.valueOf(currency),
                new BigDecimal(
                    payment == null ? (currency.equals("BRL") ? "92859.94" : "17094.67") : payment),
                fx,
                fx == null ? null : new BigDecimal("5.4321"),
                fx == null ? null : "historic",
                fx == null ? null : at.minusSeconds(3600),
                fx == null ? null : at.minusSeconds(3600),
                "monthly-ceil-30d-v1",
                UUID.randomUUID(),
                at,
                1));
    return id;
  }

  @Test
  void publishesPeriodPaginationAndTotalsContractInOpenApi() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                    "$.paths['/api/v1/settlements'].get.responses['200'].content['application/json'].schema['$ref']")
                .value("#/components/schemas/StatementResponse"))
        .andExpect(
            jsonPath("$.paths['/api/v1/settlements'].get.parameters[?(@.name=='from')].required")
                .value(org.hamcrest.Matchers.hasItem(true)))
        .andExpect(
            jsonPath(
                    "$.paths['/api/v1/settlements'].get.parameters[?(@.name=='size')].schema.maximum")
                .value(org.hamcrest.Matchers.hasItem(100)))
        .andExpect(
            jsonPath("$.components.schemas.StatementCurrencyTotal.properties.paymentTotal.type")
                .value("string"));
  }
}
