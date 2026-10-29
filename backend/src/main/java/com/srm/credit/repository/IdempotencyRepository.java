package com.srm.credit.repository;

import com.srm.credit.exception.SettlementConflictException;
import com.srm.credit.exception.SettlementConflictException.Code;
import com.srm.credit.model.SettlementIntent;
import com.srm.credit.model.StoredSettlementResponse;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class IdempotencyRepository {
  private final JdbcTemplate jdbc;

  public IdempotencyRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public boolean reserve(String actor, UUID key, String hash, Instant createdAt) {
    jdbc.queryForObject("SELECT set_config('lock_timeout', '3000ms', true)", String.class);
    try {
      int inserted =
          jdbc.update(
              """
          INSERT INTO idempotency_records (actor_id,route,key,request_hash,created_at)
          VALUES (?,?,?,?,?) ON CONFLICT (actor_id,route,key) DO NOTHING
          """,
              actor,
              SettlementIntent.ROUTE,
              key,
              hash,
              Timestamp.from(createdAt));
      jdbc.queryForObject("SELECT set_config('lock_timeout', '0', true)", String.class);
      return inserted == 1;
    } catch (DataAccessException failure) {
      // The transaction is aborted; propagate immediately, never query it again.
      for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
        if (cause instanceof SQLException sql && "55P03".equals(sql.getSQLState())) {
          throw new SettlementConflictException(Code.REQUEST_IN_PROGRESS);
        }
      }
      throw failure;
    }
  }

  public Entry find(String actor, UUID key) {
    return jdbc.queryForObject(
        "SELECT request_hash,status_code,response_body,location,settlement_id FROM idempotency_records WHERE actor_id=? AND route=? AND key=?",
        (rs, row) ->
            new Entry(
                rs.getString("request_hash"),
                new StoredSettlementResponse(
                    rs.getInt("status_code"),
                    rs.getString("response_body"),
                    rs.getString("location"),
                    rs.getObject("settlement_id", UUID.class),
                    true)),
        actor,
        SettlementIntent.ROUTE,
        key);
  }

  public void complete(
      String actor, UUID key, StoredSettlementResponse result, Instant completedAt) {
    int changed =
        jdbc.update(
            """
        UPDATE idempotency_records SET status_code=?,response_body=?,location=?,settlement_id=?,completed_at=?
        WHERE actor_id=? AND route=? AND key=? AND status_code IS NULL
        """,
            result.statusCode(),
            result.body(),
            result.location(),
            result.settlementId(),
            Timestamp.from(completedAt),
            actor,
            SettlementIntent.ROUTE,
            key);
    if (changed != 1) {
      throw new IllegalStateException("Reserva idempotente não foi concluída.");
    }
  }

  public record Entry(String hash, StoredSettlementResponse response) {}
}
