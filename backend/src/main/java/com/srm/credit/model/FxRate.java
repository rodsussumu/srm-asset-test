package com.srm.credit.model;

import com.srm.credit.exception.InvalidInputException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "fx_rates")
public class FxRate {
  public static final String PAIR = "USD/BRL";
  @Id private UUID id;

  @Column(nullable = false, length = 7)
  private String pair;

  @Column(nullable = false, precision = 16, scale = 8)
  private BigDecimal rate;

  @Column(nullable = false, length = 100)
  private String origin;

  @Column(name = "effective_at", nullable = false)
  private Instant effectiveAt;

  @Column(name = "recorded_at", nullable = false)
  private Instant recordedAt;

  protected FxRate() {}

  public FxRate(BigDecimal rate, String origin, Instant effectiveAt, Instant recordedAt) {
    if (origin == null || origin.isBlank() || origin.strip().length() > 100) {
      throw new InvalidInputException("origin", "Origem deve conter entre 1 e 100 caracteres.");
    }
    if (effectiveAt == null || !effectiveAt.equals(effectiveAt.truncatedTo(ChronoUnit.MICROS))) {
      throw new InvalidInputException(
          "effectiveAt", "Informe vigência UTC com precisão máxima de microssegundos.");
    }
    this.id = UUID.randomUUID();
    this.pair = PAIR;
    this.rate = FinancialAmounts.requireFxRate(rate).setScale(8);
    this.origin = origin.strip();
    this.effectiveAt = effectiveAt;
    this.recordedAt = recordedAt.truncatedTo(ChronoUnit.MICROS);
  }

  public UUID getId() {
    return id;
  }

  public String getPair() {
    return pair;
  }

  public BigDecimal getRate() {
    return rate;
  }

  public String getOrigin() {
    return origin;
  }

  public Instant getEffectiveAt() {
    return effectiveAt;
  }

  public Instant getRecordedAt() {
    return recordedAt;
  }
}
