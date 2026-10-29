package com.srm.credit.model;

import com.srm.credit.exception.InvalidInputException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

public final class MonthlyTermPolicy {
  public static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Sao_Paulo");
  private final Clock clock;

  public MonthlyTermPolicy(Clock clock) {
    this.clock = Objects.requireNonNull(clock);
  }

  public Term evaluate(LocalDate dueDate) {
    return evaluate(dueDate, clock.instant());
  }

  public Term evaluate(LocalDate dueDate, Instant reference) {
    if (dueDate == null) {
      throw new InvalidInputException("dueDate", "Vencimento obrigatório.");
    }
    LocalDate operationDate = LocalDate.ofInstant(reference, BUSINESS_ZONE);
    long days = ChronoUnit.DAYS.between(operationDate, dueDate);
    if (days < 1 || days > 1080) {
      throw new InvalidInputException(
          "dueDate", "Vencimento deve estar entre 1 e 1080 dias após a operação.");
    }
    return new Term(operationDate, Math.toIntExact((days + 29) / 30));
  }

  public record Term(LocalDate operationDate, int months) {}
}
