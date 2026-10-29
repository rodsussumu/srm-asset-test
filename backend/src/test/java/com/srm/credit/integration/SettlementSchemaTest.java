package com.srm.credit.integration;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class SettlementSchemaTest {
  @Container
  static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:17.11-alpine");

  static JdbcTemplate db;
  static JdbcTemplate runtime;

  @BeforeAll
  static void migrate() throws Exception {
    Flyway.configure()
        .dataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())
        .load()
        .migrate();
    db =
        new JdbcTemplate(
            new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword()));
    PG.copyFileToContainer(
        org.testcontainers.utility.MountableFile.forHostPath("../infra/postgres/bootstrap.sql"),
        "/tmp/bootstrap.sql");
    var result =
        PG.execInContainer(
            "env",
            "DATABASE_USER=srm_app",
            "DATABASE_PASSWORD=schema-test-only",
            "psql",
            "-X",
            "-U",
            PG.getUsername(),
            "-d",
            PG.getDatabaseName(),
            "-f",
            "/tmp/bootstrap.sql");
    assertThat(result.getExitCode()).as(result.getStderr()).isZero();
    runtime =
        new JdbcTemplate(
            new DriverManagerDataSource(PG.getJdbcUrl(), "srm_app", "schema-test-only"));
  }

  @Test
  void realRuntimeLoginCanCompleteReservationButCannotRewriteAudit() throws Exception {
    assertThat(runtime.queryForObject("SELECT current_user", String.class)).isEqualTo("srm_app");
    assertThat(
            runtime.queryForObject(
                "SELECT rolsuper OR rolcreaterole OR rolcreatedb FROM pg_roles WHERE rolname = current_user",
                Boolean.class))
        .isFalse();
    UUID receivable = seedReceivable();
    UUID fx = UUID.randomUUID();
    UUID settlement = UUID.randomUUID();
    UUID key = UUID.randomUUID();
    runtime.update(
        "INSERT INTO fx_rates VALUES (?, 'USD/BRL', 5.4321, 'runtime', now(), now())", fx);
    insertSettlement(settlement, receivable, fx, new BigDecimal("17094.67"));
    try (var connection =
        DriverManager.getConnection(PG.getJdbcUrl(), "srm_app", "schema-test-only")) {
      connection.setAutoCommit(false);
      try (var reserve =
          connection.prepareStatement(
              "INSERT INTO idempotency_records (actor_id,route,key,request_hash,created_at) VALUES ('role-test','/api/v1/settlements',?,repeat('a',64),now())")) {
        reserve.setObject(1, key);
        reserve.executeUpdate();
      }
      try (var complete =
          connection.prepareStatement(
              "UPDATE idempotency_records SET status_code=201,response_body='{}',location='/api/v1/settlements/test',settlement_id=?,completed_at=now() WHERE actor_id='role-test' AND key=?")) {
        complete.setObject(1, settlement);
        complete.setObject(2, key);
        assertThat(complete.executeUpdate()).isEqualTo(1);
      }
      connection.commit();
    }
    for (String sql :
        new String[] {
          "UPDATE settlements SET payment_amount = 0",
          "DELETE FROM settlements",
          "TRUNCATE settlements CASCADE",
          "UPDATE fx_rates SET rate = 1",
          "DELETE FROM fx_rates",
          "TRUNCATE fx_rates CASCADE",
          "DELETE FROM idempotency_records",
          "UPDATE idempotency_records SET response_body = '{}' WHERE actor_id='role-test'",
          "UPDATE idempotency_records SET request_hash=repeat('b',64)",
          "ALTER TABLE settlements DROP CONSTRAINT uk_settlements_receivable"
        }) {
      assertThatThrownBy(() -> runtime.execute(sql))
          .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    assertThat(
            runtime.queryForObject(
                "SELECT payment_amount FROM settlements WHERE id=?", BigDecimal.class, settlement))
        .isEqualTo(new BigDecimal("17094.67"));
    assertThat(
            runtime.queryForObject(
                "SELECT status_code FROM idempotency_records WHERE actor_id='role-test' AND key=?",
                Integer.class,
                key))
        .isEqualTo(201);
  }

  @Test
  void preservesMaximumPaymentAndEnforcesOneSettlementPerReceivable() {
    UUID receivable = seedReceivable();
    BigDecimal maximum = new BigDecimal("9999999999999999999999999999.99");
    UUID fx = UUID.randomUUID();
    db.update("INSERT INTO fx_rates VALUES (?, 'USD/BRL', 0.00000001, 'test', now(), now())", fx);
    UUID settlement = UUID.randomUUID();
    insertSettlement(settlement, receivable, fx, maximum);
    assertThat(
            db.queryForObject(
                "SELECT payment_amount FROM settlements WHERE id = ?",
                BigDecimal.class,
                settlement))
        .isEqualTo(maximum);
    assertThatThrownBy(() -> insertSettlement(UUID.randomUUID(), receivable, fx, maximum))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                insertSettlement(
                    UUID.randomUUID(), seedReceivable(), fx, maximum.add(new BigDecimal("0.01"))))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void databaseRejectsCommittingAnUnfinishedReservation() throws Exception {
    try (var connection =
        DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())) {
      connection.setAutoCommit(false);
      try (var statement =
          connection.prepareStatement(
              "INSERT INTO idempotency_records (actor_id,route,key,request_hash,created_at) VALUES ('pending','/api/v1/settlements',?,repeat('a',64),now())")) {
        statement.setObject(1, UUID.randomUUID());
        statement.executeUpdate();
      }
      assertThatThrownBy(connection::commit).isInstanceOf(SQLException.class);
      connection.rollback();
    }
    assertThat(
            db.queryForObject(
                "SELECT count(*) FROM idempotency_records WHERE actor_id = 'pending'", Long.class))
        .isZero();
  }

  static UUID seedReceivable() {
    UUID assignor = UUID.randomUUID();
    UUID receivable = UUID.randomUUID();
    db.update("INSERT INTO assignors VALUES (?, 'Schema test', now())", assignor);
    db.update(
        "INSERT INTO receivables (id,assignor_id,face_value,due_date,type,payment_currency,created_at) VALUES (?,?,100000.00,DATE '2027-01-26','DUPLICATA','USD',now())",
        receivable,
        assignor);
    return receivable;
  }

  static void insertSettlement(UUID id, UUID receivable, UUID fx, BigDecimal payment) {
    db.update(
        """
        INSERT INTO settlements (id,receivable_id,assignor_id,assignor_name,actor_id,face_value,due_date,type,
          months,operation_date,operation_at,base_rate,spread,present_value_brl,discount_brl,payment_currency,
          payment_amount,fx_rate_id,fx_rate,fx_origin,fx_effective_at,fx_recorded_at,policy_version,correlation_id,settled_at,receivable_version)
        SELECT ?,id,assignor_id,'Schema test','operator',face_value,due_date,type,3,DATE '2026-10-28',now(),
          0.0100,0.0150,92859.94,7140.06,'USD',?,?,0.00000001,'test',now(),now(),'monthly-ceil-30d-v1',?,now(),1
        FROM receivables WHERE id = ?
        """,
        id,
        payment,
        fx,
        UUID.randomUUID(),
        receivable);
  }
}
