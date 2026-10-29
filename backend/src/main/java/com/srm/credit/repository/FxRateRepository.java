package com.srm.credit.repository;

import com.srm.credit.model.FxRate;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FxRateRepository extends JpaRepository<FxRate, UUID> {
  Optional<FxRate>
      findFirstByPairAndEffectiveAtLessThanEqualAndRecordedAtLessThanEqualOrderByEffectiveAtDesc(
          String pair, Instant effectiveAt, Instant recordedAt);

  Optional<FxRate> findByPairAndEffectiveAt(String pair, Instant effectiveAt);

  @Modifying
  @Query(
      value =
          """
      INSERT INTO fx_rates (id, pair, rate, origin, effective_at, recorded_at)
      VALUES (:id, 'USD/BRL', :rate, :origin, :effectiveAt, :recordedAt)
      ON CONFLICT (pair, effective_at) DO NOTHING
      """,
      nativeQuery = true)
  int insertIfAbsent(
      @Param("id") UUID id,
      @Param("rate") BigDecimal rate,
      @Param("origin") String origin,
      @Param("effectiveAt") Instant effectiveAt,
      @Param("recordedAt") Instant recordedAt);
}
