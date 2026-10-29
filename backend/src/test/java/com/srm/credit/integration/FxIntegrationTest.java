package com.srm.credit.integration;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.srm.credit.repository.FxRateRepository;
import com.srm.credit.support.FxHttpFixture;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@WithMockUser(roles = "ADMIN")
@Import(FxIntegrationTest.FixedClockConfig.class)
class FxIntegrationTest {
  static final FxHttpFixture HTTP = startHttp();

  static FxHttpFixture startHttp() {
    try {
      return new FxHttpFixture();
    } catch (java.io.IOException failure) {
      throw new java.io.UncheckedIOException(failure);
    }
  }

  @org.junit.jupiter.api.AfterAll
  static void stopHttp() {
    HTTP.close();
  }

  static final Instant NOW = Instant.parse("2026-10-28T15:00:00Z");

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.11-alpine");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("fx.provider.url", () -> HTTP.uri().toString());
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
      return Clock.fixed(NOW, ZoneOffset.UTC);
    }
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired JdbcTemplate jdbc;
  @Autowired FxRateRepository rates;
  @Autowired com.srm.credit.service.FxRateService rateService;

  @BeforeEach
  void clean() {
    HTTP.reset();
    jdbc.update("DELETE FROM fx_rates");
  }

  @Test
  void syncRecoversAndRepeatedSyncPreservesSameIdAndSingleRow() throws Exception {
    HTTP.enqueue(503, "unavailable");
    var first =
        mvc.perform(post("/api/v1/fx-rates/sync").with(csrf()))
            .andExpect(status().isCreated())
            .andReturn();
    var original = mapper.readTree(first.getResponse().getContentAsString());
    assertThat(first.getResponse().getHeader("Location"))
        .isEqualTo("/api/v1/fx-rates/" + original.get("id").asText());
    mvc.perform(post("/api/v1/fx-rates/sync").with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(original.get("id").asText()))
        .andExpect(jsonPath("$.recordedAt").value(original.get("recordedAt").asText()));
    assertThat(rates.count()).isEqualTo(1);
    assertThat(HTTP.calls).hasValue(3);
  }

  @Test
  void syncRejectsDifferentValueForSameVigency() throws Exception {
    create("6", "2026-10-28T14:00:00Z");
    mvc.perform(post("/api/v1/fx-rates/sync").with(csrf()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("FX_RATE_EXISTS"));
    assertThat(rates.count()).isEqualTo(1);
    assertThat(rates.findAll().getFirst().getRate()).isEqualByComparingTo("6");
  }

  @Test
  void failedSyncPreservesHistoryAndReturnsSafe503WithCorrelation() throws Exception {
    create("5", NOW.toString());
    HTTP.enqueue(503, "internal-credential");
    HTTP.enqueue(503, "internal-credential");
    var response =
        mvc.perform(post("/api/v1/fx-rates/sync").with(csrf()))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("FX_PROVIDER_UNAVAILABLE"))
            .andExpect(jsonPath("$.correlationId").exists())
            .andReturn();
    assertThat(response.getResponse().getContentAsString()).doesNotContain("internal-credential");
    assertThat(rates.count()).isEqualTo(1);
    mvc.perform(get("/api/v1/fx-rates/current")).andExpect(status().isOk());
  }

  @Test
  @WithMockUser(roles = "OPERATOR")
  void operatorCannotSyncOrCallProvider() throws Exception {
    mvc.perform(post("/api/v1/fx-rates/sync").with(csrf())).andExpect(status().isForbidden());
    assertThat(HTTP.calls).hasValue(0);
    assertThat(rates.count()).isZero();
  }

  @Test
  void storesFutureQuoteAndReadsItByIdWithoutReplacingHistory() throws Exception {
    String id = create("5.4321", "2026-10-29T15:00:00Z");
    mvc.perform(get("/api/v1/fx-rates/" + id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pair").value("USD/BRL"))
        .andExpect(jsonPath("$.direction").value("BRL per USD"))
        .andExpect(jsonPath("$.rate").value("5.43210000"))
        .andExpect(jsonPath("$.recordedAt").value(NOW.toString()));
    create("6", "2026-10-30T15:00:00Z");
    mvc.perform(get("/api/v1/fx-rates?size=1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(2))
        .andExpect(jsonPath("$.items[0].rate").value("6.00000000"));
    assertThat(rates.findById(UUID.fromString(id)).orElseThrow().getRate())
        .isEqualByComparingTo("5.4321");
  }

  @Test
  void duplicateVigencyReturnsConflictAndPreservesOriginal() throws Exception {
    create("5.4321", NOW.toString());
    mvc.perform(
            post("/api/v1/fx-rates")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("6", NOW.toString())))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("FX_RATE_EXISTS"));
    assertThat(rates.count()).isEqualTo(1);
    assertThat(rates.findAll().getFirst().getRate()).isEqualByComparingTo("5.4321");
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-1", "1.000000001", "100000000", "1e1", "NaN"})
  void rejectsInvalidRates(String rate) throws Exception {
    mvc.perform(
            post("/api/v1/fx-rates")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(rate, NOW.toString())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
        .andExpect(jsonPath("$.errors.rate").exists());
    assertThat(rates.count()).isZero();
  }

  @Test
  void rejectsNumericJsonBlankOriginAndSubMicrosecondVigency() throws Exception {
    for (String json :
        new String[] {
          "{\"rate\":5.4321,\"origin\":\"manual\",\"effectiveAt\":\"" + NOW + "\"}",
          "{\"rate\":\"5\",\"origin\":\" \",\"effectiveAt\":\"" + NOW + "\"}",
          body("5", "2026-10-28T15:00:00.000000001Z")
        }) {
      mvc.perform(
              post("/api/v1/fx-rates")
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(json))
          .andExpect(status().isBadRequest());
    }
    assertThat(rates.count()).isZero();
  }

  @Test
  void postgresEnforcesRatePairAndVigencyConstraintsAndKeepsExactMaximum() {
    for (String rate : new String[] {"0", "-1", "100000000"}) {
      assertThatThrownBy(() -> rawInsert(rate, "USD/BRL", NOW))
          .isInstanceOf(DataIntegrityViolationException.class);
    }
    assertThatThrownBy(() -> rawInsert("5", "EUR/BRL", NOW))
        .isInstanceOf(DataIntegrityViolationException.class);
    rawInsert("99999999.99999999", "USD/BRL", NOW);
    assertThatThrownBy(() -> rawInsert("5", "USD/BRL", NOW))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(rates.findAll().getFirst().getRate()).isEqualTo(new BigDecimal("99999999.99999999"));
  }

  @Test
  @WithMockUser(roles = "OPERATOR")
  void operatorCannotRegisterRate() throws Exception {
    mvc.perform(
            post("/api/v1/fx-rates")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("5", NOW.toString())))
        .andExpect(status().isForbidden());
    assertThat(rates.count()).isZero();
  }

  @Test
  void missingQuoteReturns404() throws Exception {
    mvc.perform(get("/api/v1/fx-rates/" + UUID.randomUUID())).andExpect(status().isNotFound());
  }

  @Test
  void missingOrOnlyFutureQuoteIsUnavailable() throws Exception {
    mvc.perform(get("/api/v1/fx-rates/current"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("FX_RATE_UNAVAILABLE"));
    create("5", NOW.plusSeconds(1).toString());
    mvc.perform(get("/api/v1/fx-rates/current")).andExpect(status().isServiceUnavailable());
  }

  @Test
  void exactly24HoursIsValidButOneMicrosecondOlderIsExpired() throws Exception {
    create("5", NOW.minusSeconds(86400).toString());
    mvc.perform(get("/api/v1/fx-rates/current")).andExpect(status().isOk());
    assertThatThrownBy(() -> rateService.selectAt(NOW.plusNanos(1000)))
        .isInstanceOf(com.srm.credit.exception.FxRateUnavailableException.class);
  }

  @Test
  void selectsLatestEligibleVigencyIgnoringFutureAndLateRegistration() throws Exception {
    create("4", NOW.minusSeconds(7200).toString());
    String selected = create("5", NOW.minusSeconds(3600).toString());
    create("6", NOW.plusSeconds(3600).toString());
    rawInsert("7", "USD/BRL", NOW.minusSeconds(1800));
    jdbc.update(
        "UPDATE fx_rates SET recorded_at = ? WHERE rate = 7",
        java.sql.Timestamp.from(NOW.plusSeconds(1)));
    mvc.perform(get("/api/v1/fx-rates/current"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(selected))
        .andExpect(jsonPath("$.rate").value("5.00000000"));
  }

  @Test
  void registrationAfterHistoricalReferenceCannotBeSelected() throws Exception {
    create("5", NOW.minusSeconds(3600).toString());
    assertThatThrownBy(() -> rateService.selectAt(NOW.minusSeconds(1)))
        .isInstanceOf(com.srm.credit.exception.FxRateUnavailableException.class);
  }

  @ParameterizedTest
  @CsvSource({
    "DUPLICATA,100000.00,90,BRL,92859.94,7140.06,92859.94,3,0.0150",
    "CHEQUE,25000.00,60,BRL,23337.77,1662.23,23337.77,2,0.0250",
    "DUPLICATA,100000.00,90,USD,92859.94,7140.06,17094.67,3,0.0150"
  })
  @WithMockUser(roles = "OPERATOR")
  void simulationsReproduceAllGoldenCasesAndExposeInputsWithoutWrites(
      String type,
      String face,
      int days,
      String currency,
      String pv,
      String discount,
      String payment,
      int months,
      String spread)
      throws Exception {
    // Seed directly: OPERATOR cannot register FX through HTTP.
    String fxId = null;
    if (currency.equals("USD")) {
      rawInsert("5.4321", "USD/BRL", NOW.minusSeconds(3600));
      fxId = rates.findAll().getFirst().getId().toString();
    }
    var before = databaseCounts();
    var request = simulation(face, days, type, currency);
    var result =
        mvc.perform(
                post("/api/v1/pricing/simulations")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.indicative").value(true))
            .andExpect(jsonPath("$.faceValue").value(face))
            .andExpect(jsonPath("$.faceCurrency").value("BRL"))
            .andExpect(
                jsonPath("$.dueDate")
                    .value(java.time.LocalDate.of(2026, 10, 28).plusDays(days).toString()))
            .andExpect(jsonPath("$.type").value(type))
            .andExpect(jsonPath("$.paymentCurrency").value(currency))
            .andExpect(jsonPath("$.presentValueBrl").value(pv))
            .andExpect(jsonPath("$.discountBrl").value(discount))
            .andExpect(jsonPath("$.paymentAmount").value(payment))
            .andExpect(jsonPath("$.months").value(months))
            .andExpect(jsonPath("$.baseRate").value("0.0100"))
            .andExpect(jsonPath("$.spread").value(spread))
            .andExpect(jsonPath("$.operationAt").value(NOW.toString()))
            .andExpect(jsonPath("$.operationDate").value("2026-10-28"))
            .andExpect(jsonPath("$.businessTimezone").value("America/Sao_Paulo"))
            .andExpect(jsonPath("$.termConvention").value("ceil(calendarDays/30)"))
            .andExpect(jsonPath("$.roundingMode").value("HALF_EVEN"))
            .andExpect(jsonPath("$.policyVersion").value("monthly-ceil-30d-v1"));
    if (fxId != null) {
      result
          .andExpect(jsonPath("$.fxRate.id").value(fxId))
          .andExpect(jsonPath("$.fxRate.rate").value("5.43210000"))
          .andExpect(jsonPath("$.fxRate.direction").value("BRL per USD"))
          .andExpect(jsonPath("$.fxRate.pair").value("USD/BRL"))
          .andExpect(jsonPath("$.fxRate.origin").value("test"))
          .andExpect(jsonPath("$.fxRate.effectiveAt").value("2026-10-28T14:00:00Z"))
          .andExpect(jsonPath("$.fxRate.recordedAt").value(NOW.toString()));
    } else {
      result.andExpect(jsonPath("$.fxRate").doesNotExist());
    }
    assertThat(databaseCounts()).isEqualTo(before);
    assertThat(HTTP.calls).hasValue(0);
  }

  @Test
  void usdSimulationFailsWithNoQuoteExpiredQuoteOrOnlyFutureQuote() throws Exception {
    var before = databaseCounts();
    assertUnavailableSimulation();
    assertThat(databaseCounts()).isEqualTo(before);
    create("5", NOW.minusSeconds(86401).toString());
    create("6", NOW.plusSeconds(1).toString());
    before = databaseCounts();
    assertUnavailableSimulation();
    assertThat(databaseCounts()).isEqualTo(before);
    assertThat(HTTP.calls).hasValue(0);
  }

  @Test
  void brlSimulationWorksWithExpiredFxAnd31DaysUsesTwoMonths() throws Exception {
    create("5", NOW.minusSeconds(86401).toString());
    var before = databaseCounts();
    mvc.perform(
            post("/api/v1/pricing/simulations")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(simulation("100.00", 31, "DUPLICATA", "BRL")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.months").value(2));
    assertThat(databaseCounts()).isEqualTo(before);
    assertThat(HTTP.calls).hasValue(0);
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 0, 1081})
  void simulationRejectsIneligibleDueDatesWithoutWrites(int days) throws Exception {
    var before = databaseCounts();
    mvc.perform(
            post("/api/v1/pricing/simulations")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(simulation("100.00", days, "DUPLICATA", "BRL")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.dueDate").exists());
    assertThat(databaseCounts()).isEqualTo(before);
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "1.001", "10000000000000000", "1e3"})
  void simulationRejectsInvalidFace(String face) throws Exception {
    mvc.perform(
            post("/api/v1/pricing/simulations")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(simulation(face, 30, "DUPLICATA", "BRL")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.faceValue").exists());
  }

  @Test
  void simulationRejectsNumericMoneyUnknownEnumsAndExtraFields() throws Exception {
    var original =
        (com.fasterxml.jackson.databind.node.ObjectNode)
            mapper.readTree(simulation("100", 30, "DUPLICATA", "BRL"));
    for (String field : new String[] {"faceValue", "type", "paymentCurrency", "baseRate"}) {
      var request = original.deepCopy();
      if (field.equals("faceValue")) {
        request.put(field, 100);
      } else {
        request.put(field, "UNKNOWN");
      }
      mvc.perform(
              post("/api/v1/pricing/simulations")
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(request.toString()))
          .andExpect(status().isBadRequest());
    }
    assertThat(HTTP.calls).hasValue(0);
  }

  @Test
  void publishesSimulationAndCurrencyContractsInOpenApi() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/fx-rates/current'].get.responses['200']").exists())
        .andExpect(
            jsonPath(
                    "$.paths['/api/v1/pricing/simulations'].post.responses['503'].content['application/problem+json'].schema['$ref']")
                .value("#/components/schemas/ProblemDetail"))
        .andExpect(
            jsonPath("$.paths['/api/v1/pricing/simulations'].post.responses['503']").exists())
        .andExpect(jsonPath("$.paths['/api/v1/fx-rates/sync'].post.responses['503']").exists())
        .andExpect(
            jsonPath("$.components.schemas.PricingSimulationRequest.properties.faceValue.type")
                .value("string"))
        .andExpect(
            jsonPath("$.components.schemas.PricingSimulationResponse.properties.paymentAmount.type")
                .value("string"));
  }

  @Test
  @org.springframework.security.test.context.support.WithAnonymousUser
  void simulationAndCurrencyRequireAuthentication() throws Exception {
    mvc.perform(get("/api/v1/fx-rates/current")).andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/v1/pricing/simulations")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(simulation("100", 30, "CHEQUE", "BRL")))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void newMutationsRequireCsrf() throws Exception {
    mvc.perform(post("/api/v1/fx-rates/sync")).andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/pricing/simulations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(simulation("100", 30, "CHEQUE", "BRL")))
        .andExpect(status().isForbidden());
    assertThat(HTTP.calls).hasValue(0);
  }

  private void assertUnavailableSimulation() throws Exception {
    mvc.perform(
            post("/api/v1/pricing/simulations")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(simulation("100", 30, "DUPLICATA", "USD")))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("FX_RATE_UNAVAILABLE"));
  }

  private java.util.List<Long> databaseCounts() {
    return java.util.List.of(
        jdbc.queryForObject("SELECT count(*) FROM assignors", Long.class),
        jdbc.queryForObject("SELECT count(*) FROM receivables", Long.class),
        rates.count());
  }

  private String simulation(String face, int days, String type, String currency) {
    return mapper
        .createObjectNode()
        .put("faceValue", face)
        .put("dueDate", java.time.LocalDate.of(2026, 10, 28).plusDays(days).toString())
        .put("type", type)
        .put("paymentCurrency", currency)
        .toString();
  }

  String create(String rate, String effectiveAt) throws Exception {
    var result =
        mvc.perform(
                post("/api/v1/fx-rates")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(rate, effectiveAt)))
            .andExpect(status().isCreated())
            .andReturn();
    String id = mapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    assertThat(result.getResponse().getHeader("Location")).isEqualTo("/api/v1/fx-rates/" + id);
    return id;
  }

  String body(String rate, String effectiveAt) {
    return mapper
        .createObjectNode()
        .put("rate", rate)
        .put("origin", "manual")
        .put("effectiveAt", effectiveAt)
        .toString();
  }

  void rawInsert(String rate, String pair, Instant effectiveAt) {
    jdbc.update(
        "INSERT INTO fx_rates (id,pair,rate,origin,effective_at,recorded_at) VALUES (?,?,?,'test',?,?)",
        UUID.randomUUID(),
        pair,
        new BigDecimal(rate),
        java.sql.Timestamp.from(effectiveAt),
        java.sql.Timestamp.from(NOW));
  }
}
