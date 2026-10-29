package com.srm.credit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.srm.credit.model.Settlement;
import com.srm.credit.repository.IdempotencyRepository;
import com.srm.credit.repository.ReceivableRepository;
import com.srm.credit.repository.SettlementRepository;
import com.srm.credit.service.FxProviderClient;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(SettlementIntegrationTest.FixedClockConfig.class)
class SettlementIntegrationTest {
  static final Instant NOW = Instant.parse("2026-10-28T15:00:00Z");
  static final ReadHarness READS = new ReadHarness();

  @Container
  static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:17.11-alpine");

  static JdbcTemplate admin;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) throws Exception {
    PG.copyFileToContainer(
        MountableFile.forHostPath("../infra/postgres/bootstrap.sql"), "/tmp/bootstrap.sql");
    var bootstrap =
        PG.execInContainer(
            "env",
            "DATABASE_USER=srm_app",
            "DATABASE_PASSWORD=settlement-test-only",
            "psql",
            "-X",
            "-U",
            PG.getUsername(),
            "-d",
            PG.getDatabaseName(),
            "-f",
            "/tmp/bootstrap.sql");
    assertThat(bootstrap.getExitCode()).as(bootstrap.getStderr()).isZero();
    admin =
        new JdbcTemplate(
            new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword()));
    registry.add("spring.datasource.url", PG::getJdbcUrl);
    registry.add("spring.datasource.username", () -> "srm_app");
    registry.add("spring.datasource.password", () -> "settlement-test-only");
    registry.add("spring.flyway.user", PG::getUsername);
    registry.add("spring.flyway.password", PG::getPassword);
    registry.add("spring.security.user.password", () -> "integration-test-only");
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class FixedClockConfig {
    @Bean
    static org.springframework.beans.factory.config.BeanPostProcessor readHarnessProxy() {
      return new org.springframework.beans.factory.config.BeanPostProcessor() {
        @Override
        public Object postProcessAfterInitialization(Object bean, String name) {
          if (!(bean instanceof ReceivableRepository)) {
            return bean;
          }
          var proxy = new org.springframework.aop.framework.ProxyFactory(bean);
          proxy.addAdvice(
              (org.aopalliance.intercept.MethodInterceptor)
                  invocation -> {
                    Object value = invocation.proceed();
                    if (invocation.getMethod().getName().equals("findById")) {
                      UUID id = (UUID) invocation.getArguments()[0];
                      READS
                          .counts
                          .computeIfAbsent(
                              id, ignored -> new java.util.concurrent.atomic.AtomicInteger())
                          .incrementAndGet();
                      READS.hook.after(id, value);
                    }
                    return value;
                  });
          return proxy.getProxy();
        }
      };
    }

    @Bean
    @Primary
    Clock fixedClock() {
      return Clock.fixed(NOW, ZoneOffset.UTC);
    }
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired JdbcTemplate runtime;
  @MockitoSpyBean SettlementRepository settlements;
  @MockitoSpyBean IdempotencyRepository idempotency;
  @MockitoSpyBean FxProviderClient provider;

  @Autowired io.micrometer.core.instrument.MeterRegistry metrics;

  private double confirmations() {
    return metrics.get("settlements.confirmed").tag("currency", "BRL").counter().count();
  }

  @Test
  void confirmationMetricWaitsForCommitAndThreeReplaysDoNotIncrementIt() throws Exception {
    UUID id = seed("BRL", "DUPLICATA", "100000.00", 90);
    double before = confirmations();
    long timings =
        metrics.find("pricing.duration").tag("currency", "BRL").timers().stream()
            .mapToLong(io.micrometer.core.instrument.Timer::count)
            .sum();
    doAnswer(
            invocation -> {
              invocation.callRealMethod();
              assertThat(confirmations()).isEqualTo(before);
              assertThat(
                      org.springframework.transaction.support.TransactionSynchronizationManager
                          .isActualTransactionActive())
                  .isTrue();
              return null;
            })
        .when(idempotency)
        .complete(anyString(), any(UUID.class), any(), any());
    String key = UUID.randomUUID().toString();
    var request = body(id, "BRL", "92859.94", null);
    submit("operator", key, request).andExpect(status().isCreated());
    assertThat(confirmations()).isEqualTo(before + 1);
    for (int i = 0; i < 3; i++) submit("operator", key, request).andExpect(status().isCreated());
    assertThat(confirmations()).isEqualTo(before + 1);
    assertThat(
            metrics.find("pricing.duration").tag("currency", "BRL").timers().stream()
                .mapToLong(io.micrometer.core.instrument.Timer::count)
                .sum())
        .isEqualTo(timings + 1);
  }

  @BeforeEach
  void clean() {
    READS.hook = (id, value) -> {};
    READS.counts.clear();
    admin.execute(
        "TRUNCATE idempotency_records,settlements,receivables,assignors,fx_rates CASCADE");
  }

  @ParameterizedTest
  @CsvSource({
    "DUPLICATA,100000.00,90,BRL,92859.94,7140.06,92859.94",
    "CHEQUE,25000.00,60,BRL,23337.77,1662.23,23337.77",
    "DUPLICATA,100000.00,90,USD,92859.94,7140.06,17094.67"
  })
  void confirmsGoldenCasesUsingRuntimeLoginAndPreservesSnapshot(
      String type,
      String face,
      int days,
      String currency,
      String pv,
      String discount,
      String payment)
      throws Exception {
    assertThat(runtime.queryForObject("SELECT current_user", String.class)).isEqualTo("srm_app");
    UUID receivable = seed(currency, type, face, days);
    UUID fx = currency.equals("USD") ? seedFx("5.4321", NOW.minusSeconds(3600)) : null;
    var result =
        submit("operator", UUID.randomUUID().toString(), body(receivable, currency, payment, fx))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.presentValueBrl").value(pv))
            .andExpect(jsonPath("$.discountBrl").value(discount))
            .andExpect(jsonPath("$.paymentAmount").value(payment))
            .andExpect(jsonPath("$.actorId").value("operator"))
            .andExpect(jsonPath("$.faceValue").value(face))
            .andExpect(jsonPath("$.operationAt").value(NOW.toString()))
            .andExpect(jsonPath("$.operationDate").value("2026-10-28"))
            .andExpect(jsonPath("$.policyVersion").value("monthly-ceil-30d-v1"))
            .andExpect(jsonPath("$.baseRate").value("0.01000000"))
            .andExpect(jsonPath("$.receivableVersion").value(1))
            .andReturn();
    String id = mapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    assertThat(result.getResponse().getHeader("Location")).isEqualTo("/api/v1/settlements/" + id);
    mvc.perform(get("/api/v1/settlements/" + id).with(user("operator").roles("OPERATOR")))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    result.getResponse().getContentAsString(),
                    org.springframework.test.json.JsonCompareMode.STRICT));
    assertConfirmed(receivable);
    verifyNoInteractions(provider);
  }

  @Test
  void replayPrecedesRecalculationAndReturnsExactStatusBodyLocation() throws Exception {
    UUID receivable = seed("USD", "DUPLICATA", "100000.00", 90);
    UUID fx = seedFx("5.4321", NOW.minusSeconds(3600));
    String key = UUID.randomUUID().toString();
    var request = body(receivable, "USD", "17094.67", fx);
    var original = submit("operator", key, request).andExpect(status().isCreated()).andReturn();
    seedFx("6", NOW.minusSeconds(1800));
    admin.update("UPDATE assignors SET name = 'Future assignor name'");
    var retry = submit("operator", key, request).andExpect(status().isCreated()).andReturn();
    assertSameResponse(original, retry);
    mvc.perform(
            get(original.getResponse().getHeader("Location"))
                .with(user("operator").roles("OPERATOR")))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    original.getResponse().getContentAsString(),
                    org.springframework.test.json.JsonCompareMode.STRICT));
    assertConfirmed(receivable);
    assertThat(READS.counts.get(receivable)).hasValue(1);
    verifyNoInteractions(provider);
  }

  @ParameterizedTest
  @ValueSource(strings = {"receivableId", "currency", "expectedPaymentAmount", "expectedFxRateId"})
  void rejectsReusingConfirmedKeyForDifferentNormalizedContent(String field) throws Exception {
    UUID receivable = seed("USD", "DUPLICATA", "100000.00", 90);
    UUID fx = seedFx("5.4321", NOW.minusSeconds(3600));
    String key = UUID.randomUUID().toString();
    var original = body(receivable, "USD", "17094.67", fx);
    submit("operator", key, original).andExpect(status().isCreated());
    var changed = original.deepCopy();
    switch (field) {
      case "receivableId", "expectedFxRateId" -> changed.put(field, UUID.randomUUID().toString());
      case "expectedPaymentAmount" -> changed.put(field, "17094.68");
      case "currency" -> {
        changed.put(field, "BRL");
        changed.remove("expectedFxRateId");
      }
      default -> throw new AssertionError(field);
    }
    submit("operator", key, changed)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    assertConfirmed(receivable);
  }

  @Test
  void keyIsScopedByOperatorAndNormalizedDecimalFormat() throws Exception {
    UUID first = seed("BRL", "DUPLICATA", "102.50", 30);
    String key = UUID.randomUUID().toString();
    var original =
        submit("first-operator", key, body(first, "BRL", "100", null))
            .andExpect(status().isCreated())
            .andReturn();
    var retry =
        submit("first-operator", key, body(first, "BRL", "100.00", null))
            .andExpect(status().isCreated())
            .andReturn();
    assertSameResponse(original, retry);
    UUID second = seed("BRL", "DUPLICATA", "102.50", 30);
    submit("second-operator", key, body(second, "BRL", "100.0", null))
        .andExpect(status().isCreated());
    assertThat(count("settlements")).isEqualTo(2);
    assertThat(count("idempotency_records")).isEqualTo(2);
  }

  @Test
  void changedPriceOrFxRequiresExplicitNewConfirmationAndDoesNotConsumeKey() throws Exception {
    UUID receivable = seed("USD", "DUPLICATA", "100000.00", 90);
    UUID oldFx = seedFx("5.4321", NOW.minusSeconds(3600));
    UUID latestFx = seedFx("5.4321", NOW.minusSeconds(1800));
    String key = UUID.randomUUID().toString();
    submit("operator", key, body(receivable, "USD", "17094.67", oldFx))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PRICE_CHANGED"));
    assertUnsettled(receivable);
    submit("operator", key, body(receivable, "USD", "17094.68", latestFx))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PRICE_CHANGED"));
    assertUnsettled(receivable);
    submit("operator", key, body(receivable, "USD", "17094.67", latestFx))
        .andExpect(status().isCreated());
  }

  @Test
  void rejectsCurrencyMismatchBeforeLookingUpFx() throws Exception {
    UUID receivable = seed("BRL", "DUPLICATA", "100000.00", 90);
    submit(
            "operator",
            UUID.randomUUID().toString(),
            body(receivable, "USD", "17094.67", UUID.randomUUID()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.currency").exists());
    assertUnsettled(receivable);
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 0, 1081})
  void rejectsIneligiblePersistedReceivableWithoutPartialWrites(int days) throws Exception {
    UUID receivable = seed("BRL", "DUPLICATA", "100000.00", days);
    submit("operator", UUID.randomUUID().toString(), body(receivable, "BRL", "92859.94", null))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.dueDate").exists());
    assertUnsettled(receivable);
  }

  @Test
  void usdWithoutCurrentQuoteReturns503ButBrlStillConfirms() throws Exception {
    UUID usd = seed("USD", "DUPLICATA", "100000.00", 90);
    String key = UUID.randomUUID().toString();
    UUID expired = seedFx("5.4321", NOW.minusSeconds(86401));
    submit("operator", key, body(usd, "USD", "17094.67", expired))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("FX_RATE_UNAVAILABLE"));
    assertUnsettled(usd);
    UUID brl = seed("BRL", "DUPLICATA", "100000.00", 90);
    submit("operator", key, body(brl, "BRL", "92859.94", null)).andExpect(status().isCreated());
    verifyNoInteractions(provider);
  }

  @Test
  void newIntentionForSettledReceivableReturnsStableConflict() throws Exception {
    UUID receivable = seed("BRL", "DUPLICATA", "100000.00", 90);
    var request = body(receivable, "BRL", "92859.94", null);
    submit("operator", UUID.randomUUID().toString(), request).andExpect(status().isCreated());
    submit("another-operator", UUID.randomUUID().toString(), request)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RECEIVABLE_ALREADY_SETTLED"));
    assertConfirmed(receivable);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "invalid", "1-1-1-1-1"})
  void rejectsInvalidIdempotencyKeys(String key) throws Exception {
    submit("operator", key, body(UUID.randomUUID(), "BRL", "1", null))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors['Idempotency-Key']").exists());
    assertThat(count("idempotency_records")).isZero();
  }

  @Test
  void missingKeyMissingResourceAndAccessErrorsAreSemantic() throws Exception {
    var request = body(UUID.randomUUID(), "BRL", "1", null);
    mvc.perform(
            post("/api/v1/settlements")
                .with(user("operator").roles("OPERATOR"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(request.toString()))
        .andExpect(status().isBadRequest());
    submit("operator", UUID.randomUUID().toString(), request).andExpect(status().isNotFound());
    mvc.perform(
            get("/api/v1/settlements/" + UUID.randomUUID())
                .with(user("operator").roles("OPERATOR")))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/settlements/" + UUID.randomUUID()))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/v1/settlements")
                .with(user("operator").roles("OPERATOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(request.toString()))
        .andExpect(status().isForbidden());
    assertThat(count("idempotency_records")).isZero();
  }

  @ParameterizedTest
  @ValueSource(strings = {"-1", "1.001", "1e2", "10000000000000000000000000000"})
  void rejectsInvalidExpectedPayment(String amount) throws Exception {
    submit("operator", UUID.randomUUID().toString(), body(UUID.randomUUID(), "BRL", amount, null))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.expectedPaymentAmount").exists());
  }

  @Test
  void rejectsInvalidFxShapeNumericMoneyAndExtraFields() throws Exception {
    var brlWithFx = body(UUID.randomUUID(), "BRL", "1", UUID.randomUUID());
    var usdWithoutFx = body(UUID.randomUUID(), "USD", "1", null);
    var numeric = body(UUID.randomUUID(), "BRL", "1", null);
    numeric.put("expectedPaymentAmount", 1);
    var extra = body(UUID.randomUUID(), "BRL", "1", null);
    extra.put("actorId", "forged");
    for (var request : java.util.List.of(brlWithFx, usdWithoutFx, numeric, extra)) {
      submit("operator", UUID.randomUUID().toString(), request).andExpect(status().isBadRequest());
    }
    assertThat(count("idempotency_records")).isZero();
  }

  @Test
  void twoTransactionsReadTheSameVersionAndOnlyOneConfirms() throws Exception {
    UUID id = seed("BRL", "DUPLICATA", "100000.00", 90);
    var barrier = new java.util.concurrent.CyclicBarrier(2);
    var versions = new java.util.concurrent.ConcurrentLinkedQueue<Long>();
    READS.hook =
        (loadedId, result) -> {
          if (!loadedId.equals(id)) {
            return;
          }
          var loaded =
              (com.srm.credit.model.Receivable) ((java.util.Optional<?>) result).orElseThrow();
          versions.add(loaded.getVersion());
          barrier.await(10, java.util.concurrent.TimeUnit.SECONDS);
        };
    var request = body(id, "BRL", "92859.94", null);
    try (var threads = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var first =
          threads.submit(
              () -> submit("operator-a", UUID.randomUUID().toString(), request).andReturn());
      var second =
          threads.submit(
              () -> submit("operator-b", UUID.randomUUID().toString(), request).andReturn());
      var responses =
          java.util.List.of(
              first.get(15, java.util.concurrent.TimeUnit.SECONDS),
              second.get(15, java.util.concurrent.TimeUnit.SECONDS));
      assertThat(versions).containsExactlyInAnyOrder(0L, 0L);
      assertThat(responses)
          .extracting(r -> r.getResponse().getStatus())
          .containsExactlyInAnyOrder(201, 409);
      var loser =
          responses.stream()
              .filter(r -> r.getResponse().getStatus() == 409)
              .findFirst()
              .orElseThrow();
      assertThat(mapper.readTree(loser.getResponse().getContentAsString()).get("code").asText())
          .isEqualTo("RECEIVABLE_ALREADY_SETTLED");
    }
    assertConfirmed(id);
  }

  @Test
  void simultaneousRetriesWaitForTheOriginalAndReturnIdenticalSuccess() throws Exception {
    UUID id = seed("BRL", "DUPLICATA", "100000.00", 90);
    var read = new java.util.concurrent.CountDownLatch(1);
    var release = new java.util.concurrent.CountDownLatch(1);
    var followerStarted = new java.util.concurrent.CountDownLatch(1);
    holdFirstRead(id, read, release);
    signalSecondReservation(followerStarted);
    var request = body(id, "BRL", "92859.94", null);
    String key = UUID.randomUUID().toString();
    try (var threads = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      try {
        var first = threads.submit(() -> submit("operator", key, request).andReturn());
        await(read);
        var second = threads.submit(() -> submit("operator", key, request).andReturn());
        await(followerStarted);
        release.countDown();
        var original = first.get(10, java.util.concurrent.TimeUnit.SECONDS);
        var retry = second.get(10, java.util.concurrent.TimeUnit.SECONDS);
        assertThat(original.getResponse().getStatus()).isEqualTo(201);
        assertSameResponse(original, retry);
      } finally {
        release.countDown();
      }
    }
    assertConfirmed(id);
  }

  @Test
  void reservationWaitTimesOutAtThreeSecondsAndClientCanRetry() throws Exception {
    UUID id = seed("BRL", "DUPLICATA", "100000.00", 90);
    var read = new java.util.concurrent.CountDownLatch(1);
    var release = new java.util.concurrent.CountDownLatch(1);
    holdFirstRead(id, read, release);
    var request = body(id, "BRL", "92859.94", null);
    String key = UUID.randomUUID().toString();
    try (var threads = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      try {
        var first = threads.submit(() -> submit("operator", key, request).andReturn());
        await(read);
        long start = System.nanoTime();
        var second =
            threads.submit(
                () ->
                    submit("operator", key, request)
                        .andExpect(status().isConflict())
                        .andExpect(jsonPath("$.code").value("REQUEST_IN_PROGRESS"))
                        .andExpect(header().string("Retry-After", "1"))
                        .andReturn());
        second.get(6, java.util.concurrent.TimeUnit.SECONDS);
        assertThat(java.time.Duration.ofNanos(System.nanoTime() - start))
            .isBetween(java.time.Duration.ofMillis(2800), java.time.Duration.ofSeconds(5));
        assertThat(count("settlements")).isZero();
        release.countDown();
        var original = first.get(10, java.util.concurrent.TimeUnit.SECONDS);
        var retry = submit("operator", key, request).andExpect(status().isCreated()).andReturn();
        assertSameResponse(original, retry);
      } finally {
        release.countDown();
      }
    }
    assertConfirmed(id);
  }

  @Test
  void waitingRetryCanTakeOverAfterTheOriginalTransactionRollsBack() throws Exception {
    UUID id = seed("BRL", "DUPLICATA", "100000.00", 90);
    var read = new java.util.concurrent.CountDownLatch(1);
    var release = new java.util.concurrent.CountDownLatch(1);
    var followerStarted = new java.util.concurrent.CountDownLatch(1);
    holdFirstRead(id, read, release);
    signalSecondReservation(followerStarted);
    var failOnce = new java.util.concurrent.atomic.AtomicBoolean(true);
    doAnswer(
            invocation -> {
              invocation.callRealMethod();
              if (failOnce.getAndSet(false)) {
                throw new IllegalStateException("injected rollback");
              }
              return null;
            })
        .when(settlements)
        .save(any(Settlement.class));
    var request = body(id, "BRL", "92859.94", null);
    String key = UUID.randomUUID().toString();
    try (var threads = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      try {
        var first = threads.submit(() -> submit("operator", key, request).andReturn());
        await(read);
        var second = threads.submit(() -> submit("operator", key, request).andReturn());
        await(followerStarted);
        release.countDown();
        assertThat(first.get(10, java.util.concurrent.TimeUnit.SECONDS).getResponse().getStatus())
            .isEqualTo(500);
        assertThat(second.get(10, java.util.concurrent.TimeUnit.SECONDS).getResponse().getStatus())
            .isEqualTo(201);
      } finally {
        release.countDown();
      }
    }
    assertConfirmed(id);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void failureBeforeOrAfterAuditInsertRollsBackAllWritesAndDoesNotConsumeKey(boolean afterInsert)
      throws Exception {
    double beforeMetric = confirmations();
    UUID id = seed("BRL", "DUPLICATA", "100000.00", 90);
    doAnswer(
            invocation -> {
              assertThat(
                      runtime.queryForObject(
                          "SELECT status FROM receivables WHERE id=?", String.class, id))
                  .isEqualTo("SETTLED");
              assertThat(
                      runtime.queryForObject(
                          "SELECT version FROM receivables WHERE id=?", Long.class, id))
                  .isEqualTo(1);
              if (afterInsert) {
                invocation.callRealMethod();
                assertThat(
                        runtime.queryForObject(
                            "SELECT count(*) FROM settlements WHERE receivable_id=?",
                            Long.class,
                            id))
                    .isEqualTo(1);
              }
              throw new IllegalStateException("SELECT password FROM hidden_internal_table");
            })
        .when(settlements)
        .save(any(Settlement.class));
    var request = body(id, "BRL", "92859.94", null);
    String key = UUID.randomUUID().toString();
    var failure =
        submit("operator", key, request)
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(jsonPath("$.correlationId").exists())
            .andReturn();
    assertThat(failure.getResponse().getContentAsString())
        .doesNotContain("SELECT", "password", "hidden_internal_table");
    assertUnsettled(id);
    assertThat(confirmations()).isEqualTo(beforeMetric);
    doCallRealMethod().when(settlements).save(any(Settlement.class));
    submit("operator", key, request).andExpect(status().isCreated());
    assertConfirmed(id);
  }

  @Test
  void deferredCommitFailureCannotReturnSuccessAndRollsBackTheCompletedResult() throws Exception {
    double beforeMetric = confirmations();
    UUID id = seed("BRL", "DUPLICATA", "100000.00", 90);
    admin.execute(
        "CREATE FUNCTION test_fail_commit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'injected deferred failure' USING ERRCODE='23514'; END; $$");
    admin.execute(
        "CREATE CONSTRAINT TRIGGER test_fail_commit AFTER INSERT ON settlements DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION test_fail_commit()");
    var request = body(id, "BRL", "92859.94", null);
    String key = UUID.randomUUID().toString();
    try {
      submit("operator", key, request)
          .andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
      assertUnsettled(id);
      assertThat(confirmations()).isEqualTo(beforeMetric);
    } finally {
      admin.execute("DROP TRIGGER test_fail_commit ON settlements");
      admin.execute("DROP FUNCTION test_fail_commit()");
    }
    submit("operator", key, request).andExpect(status().isCreated());
    assertConfirmed(id);
  }

  @Autowired org.springframework.web.context.WebApplicationContext context;

  @Test
  void retryRecoversOriginalResponseAfterTransportFailsFollowingCommit() throws Exception {
    UUID id = seed("BRL", "DUPLICATA", "100000.00", 90);
    String key = UUID.randomUUID().toString();
    var request = body(id, "BRL", "92859.94", null);
    var dropped =
        org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
            .apply(
                org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
                    .springSecurity())
            .addFilters(
                context.getBean(com.srm.credit.config.CorrelationFilter.class),
                (jakarta.servlet.Filter)
                    (req, res, chain) -> {
                      chain.doFilter(req, res);
                      throw new java.io.IOException("connection lost after commit");
                    })
            .build();
    assertThatThrownBy(
            () ->
                dropped.perform(
                    post("/api/v1/settlements")
                        .with(user("operator").roles("OPERATOR"))
                        .with(csrf())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request.toString())))
        .isInstanceOf(java.io.IOException.class)
        .hasMessageContaining("connection lost after commit");
    assertConfirmed(id);
    var stored =
        admin.queryForMap(
            "SELECT status_code,response_body,location FROM idempotency_records WHERE key=?",
            UUID.fromString(key));
    var retry = submit("operator", key, request).andExpect(status().isCreated()).andReturn();
    assertThat(retry.getResponse().getStatus()).isEqualTo(stored.get("status_code"));
    assertThat(retry.getResponse().getContentAsString()).isEqualTo(stored.get("response_body"));
    assertThat(retry.getResponse().getHeader("Location")).isEqualTo(stored.get("location"));
    assertConfirmed(id);
  }

  @Test
  void canConfirmZeroRoundedPaymentWithoutMisclassifyingItAsInvalidInput() throws Exception {
    UUID id = seed("BRL", "DUPLICATA", "0.01", 1080);
    submit("operator", UUID.randomUUID().toString(), body(id, "BRL", "0", null))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.paymentAmount").value("0.00"))
        .andExpect(jsonPath("$.discountBrl").value("0.01"));
  }

  @Test
  void openApiDescribesIdempotencyHeaderAndSnapshotResponse() throws Exception {
    mvc.perform(get("/v3/api-docs").with(user("operator").roles("OPERATOR")))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                    "$.paths['/api/v1/settlements'].post.responses['201'].content['application/json'].schema['$ref']")
                .value("#/components/schemas/SettlementResponse"))
        .andExpect(
            jsonPath(
                    "$.paths['/api/v1/settlements'].post.parameters[?(@.name=='Idempotency-Key')].required")
                .value(org.hamcrest.Matchers.hasItem(true)))
        .andExpect(jsonPath("$.paths['/api/v1/settlements'].post.responses['409']").exists())
        .andExpect(
            jsonPath(
                    "$.paths['/api/v1/settlements/{id}'].get.responses['200'].content['application/json'].schema['$ref']")
                .value("#/components/schemas/SettlementResponse"))
        .andExpect(
            jsonPath(
                    "$.paths['/api/v1/settlements'].post.responses['409'].content['application/problem+json'].schema['$ref']")
                .value("#/components/schemas/ProblemDetail"))
        .andExpect(
            jsonPath(
                    "$.components.schemas.CreateSettlementRequest.properties.expectedPaymentAmount.type")
                .value("string"));
  }

  void holdFirstRead(
      UUID id,
      java.util.concurrent.CountDownLatch read,
      java.util.concurrent.CountDownLatch release) {
    var first = new java.util.concurrent.atomic.AtomicBoolean(true);
    READS.hook =
        (loadedId, value) -> {
          if (loadedId.equals(id) && first.getAndSet(false)) {
            read.countDown();
            await(release);
          }
        };
  }

  void signalSecondReservation(java.util.concurrent.CountDownLatch entered) {
    var attempts = new java.util.concurrent.atomic.AtomicInteger();
    doAnswer(
            invocation -> {
              if (attempts.incrementAndGet() == 2) {
                entered.countDown();
              }
              return invocation.callRealMethod();
            })
        .when(idempotency)
        .reserve(anyString(), any(UUID.class), anyString(), any(Instant.class));
  }

  static void await(java.util.concurrent.CountDownLatch latch) throws InterruptedException {
    assertThat(latch.await(10, java.util.concurrent.TimeUnit.SECONDS))
        .as("Coordinated transaction reached test barrier")
        .isTrue();
  }

  static class ReadHarness {
    final java.util.concurrent.ConcurrentHashMap<UUID, java.util.concurrent.atomic.AtomicInteger>
        counts = new java.util.concurrent.ConcurrentHashMap<>();
    volatile ReadHook hook = (id, value) -> {};
  }

  @FunctionalInterface
  interface ReadHook {
    void after(UUID id, Object value) throws Exception;
  }

  UUID seed(String currency, String type, String face, int days) {
    UUID assignor = UUID.randomUUID();
    UUID receivable = UUID.randomUUID();
    admin.update(
        "INSERT INTO assignors VALUES (?, 'Original assignor', ?)", assignor, Timestamp.from(NOW));
    admin.update(
        "INSERT INTO receivables (id,assignor_id,face_value,due_date,type,payment_currency,created_at) VALUES (?,?,?,?,?,?,?)",
        receivable,
        assignor,
        new BigDecimal(face),
        java.sql.Date.valueOf(LocalDate.of(2026, 10, 28).plusDays(days)),
        type,
        currency,
        Timestamp.from(NOW));
    return receivable;
  }

  UUID seedFx(String rate, Instant effective) {
    UUID id = UUID.randomUUID();
    admin.update(
        "INSERT INTO fx_rates VALUES (?, 'USD/BRL', ?, 'local-test', ?, ?)",
        id,
        new BigDecimal(rate),
        Timestamp.from(effective),
        Timestamp.from(NOW));
    return id;
  }

  ObjectNode body(UUID receivable, String currency, String amount, UUID fx) {
    var value =
        mapper
            .createObjectNode()
            .put("receivableId", receivable.toString())
            .put("currency", currency)
            .put("expectedPaymentAmount", amount);
    if (fx != null) {
      value.put("expectedFxRateId", fx.toString());
    }
    return value;
  }

  org.springframework.test.web.servlet.ResultActions submit(
      String actor, String key, ObjectNode body) throws Exception {
    return mvc.perform(
        post("/api/v1/settlements")
            .with(user(actor).roles("OPERATOR"))
            .with(csrf())
            .header("Idempotency-Key", key)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body.toString()));
  }

  long count(String table) {
    return admin.queryForObject("SELECT count(*) FROM " + table, Long.class);
  }

  void assertConfirmed(UUID receivable) {
    assertThat(count("settlements")).isEqualTo(1);
    assertThat(count("idempotency_records")).isEqualTo(1);
    assertThat(
            admin.queryForObject(
                "SELECT status FROM receivables WHERE id=?", String.class, receivable))
        .isEqualTo("SETTLED");
    assertThat(
            admin.queryForObject(
                "SELECT version FROM receivables WHERE id=?", Long.class, receivable))
        .isEqualTo(1);
  }

  void assertUnsettled(UUID receivable) {
    assertThat(count("settlements")).isZero();
    assertThat(count("idempotency_records")).isZero();
    assertThat(
            admin.queryForObject(
                "SELECT status FROM receivables WHERE id=?", String.class, receivable))
        .isEqualTo("AVAILABLE");
    assertThat(
            admin.queryForObject(
                "SELECT version FROM receivables WHERE id=?", Long.class, receivable))
        .isZero();
  }

  void assertSameResponse(MvcResult original, MvcResult retry) {
    assertThat(retry.getResponse().getStatus()).isEqualTo(original.getResponse().getStatus());
    assertThat(retry.getResponse().getContentAsByteArray())
        .isEqualTo(original.getResponse().getContentAsByteArray());
    assertThat(retry.getResponse().getHeader("Location"))
        .isEqualTo(original.getResponse().getHeader("Location"));
  }
}
