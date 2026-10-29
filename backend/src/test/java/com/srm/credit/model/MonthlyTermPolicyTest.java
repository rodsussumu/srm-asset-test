package com.srm.credit.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.srm.credit.exception.InvalidInputException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class MonthlyTermPolicyTest {
  private final LocalDate operationDate = LocalDate.of(2026, 10, 28);
  private final MonthlyTermPolicy policy =
      new MonthlyTermPolicy(Clock.fixed(Instant.parse("2026-10-28T15:00:00Z"), ZoneOffset.UTC));

  @ParameterizedTest
  @CsvSource({"1,1", "30,1", "31,2", "1080,36"})
  void calculatesIntegerMonthsWithCeiling(int days, int expectedMonths) {
    var term = policy.evaluate(operationDate.plusDays(days));
    assertThat(term.months()).isEqualTo(expectedMonths);
    assertThat(term.operationDate()).isEqualTo(operationDate);
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 0, 1081})
  void rejectsIneligibleDates(int days) {
    assertThatThrownBy(() -> policy.evaluate(operationDate.plusDays(days)))
        .isInstanceOf(InvalidInputException.class);
  }

  @Test
  void rejectsMissingDate() {
    assertThatThrownBy(() -> policy.evaluate(null)).isInstanceOf(InvalidInputException.class);
  }

  @Test
  void eligibilityChangesAtMidnightInSaoPauloRatherThanUtc() {
    Instant beforeMidnight = Instant.parse("2026-10-29T02:59:59Z");
    var before = new MonthlyTermPolicy(Clock.fixed(beforeMidnight, ZoneOffset.UTC));
    var after = new MonthlyTermPolicy(Clock.fixed(beforeMidnight.plusSeconds(1), ZoneOffset.UTC));
    LocalDate dueDate = LocalDate.of(2026, 10, 29);
    assertThat(before.evaluate(dueDate).months()).isEqualTo(1);
    assertThatThrownBy(() -> after.evaluate(dueDate)).isInstanceOf(InvalidInputException.class);
  }
}
