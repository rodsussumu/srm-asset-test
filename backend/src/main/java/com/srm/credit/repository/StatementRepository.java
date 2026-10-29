package com.srm.credit.repository;

import com.srm.credit.model.CurrencyTotal;
import com.srm.credit.model.PaymentCurrency;
import com.srm.credit.model.Settlement;
import com.srm.credit.model.StatementQuery;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StatementRepository {
  private final NamedParameterJdbcTemplate jdbc;

  public StatementRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Settlement> list(StatementQuery query) {
    var sql = criteria(query);
    return jdbc.query(sql.items(), sql.parameters(), (rs, row) -> SettlementRepository.read(rs));
  }

  public long count(StatementQuery query) {
    var sql = criteria(query);
    return jdbc.queryForObject(sql.count(), sql.parameters(), Long.class);
  }

  public List<CurrencyTotal> totals(StatementQuery query) {
    var sql = criteria(query);
    return jdbc.query(
        sql.totals(),
        sql.parameters(),
        (rs, row) ->
            new CurrencyTotal(
                PaymentCurrency.valueOf(rs.getString("payment_currency")),
                rs.getLong("count"),
                rs.getBigDecimal("payment_total")));
  }

  static QuerySql criteria(StatementQuery query) {
    String where = " WHERE s.settled_at >= :from AND s.settled_at < :to";
    var parameters =
        new MapSqlParameterSource("from", timestampCeiling(query.from()))
            .addValue("to", timestampCeiling(query.to()))
            .addValue("limit", query.size())
            .addValue("offset", query.offset());
    if (query.assignorId() != null) {
      where += " AND s.assignor_id = :assignorId";
      parameters.addValue("assignorId", query.assignorId());
    }
    if (query.currency() != null) {
      where += " AND s.payment_currency = :currency";
      parameters.addValue("currency", query.currency().name());
    }
    return new QuerySql(where, parameters);
  }

  // A timestamp stored in PostgreSQL is on the microsecond grid. Ceiling both bounds
  // preserves >= from and < to exactly, even when an ISO query contains nanoseconds.
  private static Timestamp timestampCeiling(Instant value) {
    Instant micros = value.truncatedTo(ChronoUnit.MICROS);
    return Timestamp.from(value.equals(micros) ? micros : micros.plusNanos(1000));
  }

  record QuerySql(String where, MapSqlParameterSource parameters) {
    String totals() {
      return "SELECT s.payment_currency, count(*) AS count, sum(s.payment_amount) AS payment_total FROM settlements s"
          + where
          + " GROUP BY s.payment_currency ORDER BY s.payment_currency";
    }

    String items() {
      return "SELECT s.* FROM settlements s"
          + where
          + " ORDER BY s.settled_at DESC, s.id DESC LIMIT :limit OFFSET :offset";
    }

    String count() {
      return "SELECT count(*) FROM settlements s" + where;
    }
  }
}
