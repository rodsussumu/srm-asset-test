package com.srm.credit.repository;

import com.srm.credit.model.PaymentCurrency;
import com.srm.credit.model.ReceivableType;
import com.srm.credit.model.Settlement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SettlementRepository {
  private final JdbcTemplate jdbc;

  public SettlementRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void save(Settlement value) {
    jdbc.update(
        """
        INSERT INTO settlements (id,receivable_id,assignor_id,assignor_name,actor_id,face_value,due_date,type,months,
          operation_date,operation_at,base_rate,spread,present_value_brl,discount_brl,payment_currency,payment_amount,
          fx_rate_id,fx_rate,fx_origin,fx_effective_at,fx_recorded_at,policy_version,correlation_id,settled_at,receivable_version)
        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        """,
        value.id(),
        value.receivableId(),
        value.assignorId(),
        value.assignorName(),
        value.actorId(),
        value.faceValue(),
        java.sql.Date.valueOf(value.dueDate()),
        value.type().name(),
        value.months(),
        java.sql.Date.valueOf(value.operationDate()),
        Timestamp.from(value.operationAt()),
        value.baseRate(),
        value.spread(),
        value.presentValueBrl(),
        value.discountBrl(),
        value.paymentCurrency().name(),
        value.paymentAmount(),
        value.fxRateId(),
        value.fxRate(),
        value.fxOrigin(),
        timestamp(value.fxEffectiveAt()),
        timestamp(value.fxRecordedAt()),
        value.policyVersion(),
        value.correlationId(),
        Timestamp.from(value.settledAt()),
        value.receivableVersion());
  }

  public Optional<Settlement> find(UUID id) {
    return jdbc.query("SELECT * FROM settlements WHERE id=?", (rs, row) -> read(rs), id).stream()
        .findFirst();
  }

  private static Settlement read(ResultSet rs) throws SQLException {
    return new Settlement(
        rs.getObject("id", UUID.class),
        rs.getObject("receivable_id", UUID.class),
        rs.getObject("assignor_id", UUID.class),
        rs.getString("assignor_name"),
        rs.getString("actor_id"),
        rs.getBigDecimal("face_value"),
        rs.getDate("due_date").toLocalDate(),
        ReceivableType.valueOf(rs.getString("type")),
        rs.getInt("months"),
        rs.getDate("operation_date").toLocalDate(),
        rs.getTimestamp("operation_at").toInstant(),
        rs.getBigDecimal("base_rate"),
        rs.getBigDecimal("spread"),
        rs.getBigDecimal("present_value_brl"),
        rs.getBigDecimal("discount_brl"),
        PaymentCurrency.valueOf(rs.getString("payment_currency")),
        rs.getBigDecimal("payment_amount"),
        rs.getObject("fx_rate_id", UUID.class),
        rs.getBigDecimal("fx_rate"),
        rs.getString("fx_origin"),
        instant(rs, "fx_effective_at"),
        instant(rs, "fx_recorded_at"),
        rs.getString("policy_version"),
        rs.getObject("correlation_id", UUID.class),
        rs.getTimestamp("settled_at").toInstant(),
        rs.getLong("receivable_version"));
  }

  private static Timestamp timestamp(Instant value) {
    return value == null ? null : Timestamp.from(value);
  }

  private static Instant instant(ResultSet rs, String name) throws SQLException {
    var value = rs.getTimestamp(name);
    return value == null ? null : value.toInstant();
  }
}
