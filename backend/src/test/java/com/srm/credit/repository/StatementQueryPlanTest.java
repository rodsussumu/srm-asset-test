package com.srm.credit.repository;

import static org.assertj.core.api.Assertions.*;

import com.srm.credit.model.PaymentCurrency;
import com.srm.credit.model.StatementQuery;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class StatementQueryPlanTest {
  @Container
  static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:17.11-alpine");

  @Test
  void generatesReproducibleWorkloadAndExplainsTheActualRepositoryQueries() throws Exception {
    Flyway.configure()
        .dataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())
        .load()
        .migrate();
    var owner = new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
    var admin = new JdbcTemplate(owner);
    for (int run = 0; run < 2; run++) {
      try (var connection = owner.getConnection()) {
        ScriptUtils.executeSqlScript(
            connection, new FileSystemResource("../scripts/benchmark/seed-statements.sql"));
      }
    }
    assertThat(admin.queryForObject("SELECT count(*) FROM receivables", Long.class))
        .isEqualTo(10000);
    assertThat(admin.queryForObject("SELECT count(*) FROM settlements", Long.class))
        .isEqualTo(5000);
    assertThat(
            admin.queryForObject(
                "SELECT count(*) FROM settlements WHERE payment_currency='USD'", Long.class))
        .isEqualTo(2500);
    assertThat(
            admin.queryForObject(
                "SELECT count(*) FROM settlements WHERE fx_effective_at > operation_at OR operation_at-fx_effective_at > INTERVAL '24 hours'",
                Long.class))
        .isZero();
    assertThat(
            admin.queryForList(
                "SELECT indexname FROM pg_indexes WHERE tablename='settlements'", String.class))
        .contains(
            "settlements_settled_idx",
            "settlements_assignor_settled_idx",
            "settlements_currency_settled_idx");
    admin.execute("VACUUM (ANALYZE) settlements");
    admin.execute(
        "CREATE ROLE statement_reader LOGIN PASSWORD 'plan-test-only' IN ROLE srm_runtime");
    var jdbc =
        new NamedParameterJdbcTemplate(
            new DriverManagerDataSource(PG.getJdbcUrl(), "statement_reader", "plan-test-only"));
    var repository = new StatementRepository(jdbc);
    Instant from = Instant.parse("2026-01-01T00:00:00Z"),
        to = Instant.parse("2026-07-01T00:00:00Z");
    UUID assignor =
        admin.queryForObject("SELECT md5('statement-benchmark-assignor-1')::uuid", UUID.class);
    var queries =
        java.util.List.of(
            new StatementQuery(from, to, null, null, 0, 20),
            new StatementQuery(from, to, assignor, null, 0, 20),
            new StatementQuery(from, to, null, PaymentCurrency.USD, 0, 20),
            new StatementQuery(
                from, from.plusSeconds(86400), assignor, PaymentCurrency.USD, 0, 20));
    var report =
        new StringBuilder(
            "Statement query plans — synthetic 10000 receivables / 5000 settlements\n");
    report.append(admin.queryForObject("SELECT version()", String.class)).append('\n');
    report
        .append("Java ")
        .append(System.getProperty("java.version"))
        .append("; client ")
        .append(System.getProperty("os.name"))
        .append('/')
        .append(System.getProperty("os.arch"))
        .append("; client processors=")
        .append(Runtime.getRuntime().availableProcessors())
        .append('\n');
    report.append(
        "Data and indexes warm after load + VACUUM ANALYZE; role=statement_reader; page=0 size=20. Not HTTP latency or p95.\n");
    for (var query : queries) {
      var sql = StatementRepository.criteria(query);
      report.append("\nFILTER ").append(query).append("\n");
      assertThat(repository.list(query)).hasSizeLessThanOrEqualTo(20);
      assertThat(repository.count(query))
          .isEqualTo(repository.totals(query).stream().mapToLong(v -> v.count()).sum());
      for (var statement : java.util.List.of(sql.items(), sql.count(), sql.totals())) {
        report.append(statement).append('\n');
        jdbc.queryForList(
                "EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT) " + statement,
                sql.parameters(),
                String.class)
            .forEach(line -> report.append(line).append('\n'));
      }
    }
    Files.createDirectories(Path.of("target"));
    Files.writeString(Path.of("target/statement-query-plans.txt"), report);
  }
}
