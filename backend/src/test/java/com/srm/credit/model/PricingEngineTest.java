package com.srm.credit.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.srm.credit.exception.InvalidInputException;
import com.srm.credit.model.strategy.ChequeStrategy;
import com.srm.credit.model.strategy.DuplicataStrategy;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class PricingEngineTest {
  private final PricingEngine engine =
      new PricingEngine(
          new BigDecimal("0.0100"), List.of(new DuplicataStrategy(), new ChequeStrategy()));

  @ParameterizedTest(name = "Golden case {0}")
  @CsvSource({
    "C1,DUPLICATA,100000.00,3,BRL,1,92859.94,7140.06,92859.94",
    "C2,CHEQUE,25000.00,2,BRL,1,23337.77,1662.23,23337.77",
    "C3,DUPLICATA,100000.00,3,USD,5.4321,92859.94,7140.06,17094.67"
  })
  void goldenCases(
      String id,
      ReceivableType type,
      String face,
      int months,
      PaymentCurrency currency,
      String fxRate,
      String pv,
      String discount,
      String payment) {
    var result =
        engine.calculate(new BigDecimal(face), type, months, currency, new BigDecimal(fxRate));
    assertThat(result.presentValueBrl()).isEqualTo(new BigDecimal(pv));
    assertThat(result.discountBrl()).isEqualTo(new BigDecimal(discount));
    assertThat(result.paymentAmount()).isEqualTo(new BigDecimal(payment));
    assertThat(result.paymentCurrency()).isEqualTo(currency);
    assertThat(result.policyVersion()).isEqualTo("monthly-ceil-30d-v1");
  }

  @Test
  void convertsTheAlreadyRoundedBrlPresentValue() {
    var result =
        engine.calculate(
            new BigDecimal("0.01"),
            ReceivableType.DUPLICATA,
            1,
            PaymentCurrency.USD,
            new BigDecimal("0.001"));
    assertThat(result.presentValueBrl()).isEqualTo(new BigDecimal("0.01"));
    assertThat(result.paymentAmount()).isEqualTo(new BigDecimal("10.00"));
  }

  @Test
  void zeroTermReturnsFaceValueWithoutDiscount() {
    var result =
        engine.calculate(
            new BigDecimal("100.00"), ReceivableType.CHEQUE, 0, PaymentCurrency.BRL, null);
    assertThat(result.presentValueBrl()).isEqualTo(new BigDecimal("100.00"));
    assertThat(result.discountBrl()).isEqualTo(new BigDecimal("0.00"));
    assertThat(result.fxRate()).isNull();
  }

  @Test
  void halfEvenChoosesTheEvenCent() {
    assertThat(FinancialAmounts.roundMoney(new BigDecimal("1.005")))
        .isEqualTo(new BigDecimal("1.00"));
    assertThat(FinancialAmounts.roundMoney(new BigDecimal("1.015")))
        .isEqualTo(new BigDecimal("1.02"));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(
      strings = {"0", "-1", "1.001", "10000000000000000.00", "1e3", "NaN", "01.00", " 1.00"})
  void rejectsInvalidFaceStrings(String value) {
    assertThatThrownBy(() -> FinancialAmounts.parseFace(value))
        .isInstanceOf(InvalidInputException.class);
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 37})
  void rejectsInvalidTerms(int months) {
    assertThatThrownBy(
            () ->
                engine.calculate(
                    BigDecimal.ONE, ReceivableType.DUPLICATA, months, PaymentCurrency.BRL, null))
        .isInstanceOf(InvalidInputException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-1", "0.000000001", "100000000.00"})
  void rejectsInvalidFxRates(String fxRate) {
    assertThatThrownBy(
            () ->
                engine.calculate(
                    BigDecimal.ONE,
                    ReceivableType.CHEQUE,
                    1,
                    PaymentCurrency.USD,
                    new BigDecimal(fxRate)))
        .isInstanceOf(InvalidInputException.class);
  }

  @Test
  void supportsFinancialBoundsWithoutOverflow() {
    var result =
        engine.calculate(
            FinancialAmounts.parseFace("9999999999999999.99"),
            ReceivableType.CHEQUE,
            36,
            PaymentCurrency.USD,
            new BigDecimal("0.00000001"));
    assertThat(result.presentValueBrl()).isPositive();
    assertThat(result.paymentAmount().precision()).isLessThanOrEqualTo(30);
    assertThat(result.discountBrl().add(result.presentValueBrl()))
        .isEqualTo(new BigDecimal("9999999999999999.99"));
  }

  @Test
  void doesNotFallBackToAnotherStrategy() {
    var incomplete = new PricingEngine(new BigDecimal("0.01"), List.of(new DuplicataStrategy()));
    assertThatThrownBy(
            () ->
                incomplete.calculate(
                    BigDecimal.ONE, ReceivableType.CHEQUE, 1, PaymentCurrency.BRL, null))
        .isInstanceOf(InvalidInputException.class);
    assertThatThrownBy(() -> engine.calculate(BigDecimal.ONE, null, 1, PaymentCurrency.BRL, null))
        .isInstanceOf(InvalidInputException.class);
    assertThat(new DuplicataStrategy().monthlySpread()).isEqualByComparingTo("0.0150");
    assertThat(new ChequeStrategy().monthlySpread()).isEqualByComparingTo("0.0250");
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"-0.01", "0.000000001", "100"})
  void rejectsInvalidBaseRateBeforePricing(BigDecimal baseRate) {
    assertThatThrownBy(() -> new PricingEngine(baseRate, List.of(new DuplicataStrategy())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Taxa base inválida.");
  }

  @Test
  void permitsZeroBaseRateWhileStillApplyingTheTypeSpread() {
    var zeroBase = new PricingEngine(BigDecimal.ZERO, List.of(new DuplicataStrategy()));
    var result =
        zeroBase.calculate(
            new BigDecimal("100.00"), ReceivableType.DUPLICATA, 1, PaymentCurrency.BRL, null);
    assertThat(result.presentValueBrl()).isEqualTo(new BigDecimal("98.52"));
    assertThat(result.baseRate()).isEqualTo(BigDecimal.ZERO);
    assertThat(result.spread()).isEqualByComparingTo("0.0150");
  }

  @Test
  void rejectsAmbiguousConfigurationWithDuplicateStrategies() {
    assertThatThrownBy(
            () ->
                new PricingEngine(
                    new BigDecimal("0.01"),
                    List.of(new DuplicataStrategy(), new DuplicataStrategy())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Strategy duplicada: DUPLICATA");
  }

  @Test
  void requiresPaymentCurrencyInsteadOfAssumingBrl() {
    assertThatThrownBy(
            () -> engine.calculate(new BigDecimal("100.00"), ReceivableType.CHEQUE, 1, null, null))
        .isInstanceOfSatisfying(
            InvalidInputException.class,
            error -> assertThat(error.field()).isEqualTo("paymentCurrency"));
  }

  @Test
  void requiresFxRateForUsdPayment() {
    assertThatThrownBy(
            () ->
                engine.calculate(
                    new BigDecimal("100.00"), ReceivableType.CHEQUE, 1, PaymentCurrency.USD, null))
        .isInstanceOfSatisfying(
            InvalidInputException.class, error -> assertThat(error.field()).isEqualTo("fxRate"));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"0", "-1", "1.001", "10000000000000000.00"})
  void rejectsInvalidDecimalFacesEvenWithoutApiStringValidation(BigDecimal face) {
    assertThatThrownBy(
            () -> engine.calculate(face, ReceivableType.CHEQUE, 1, PaymentCurrency.BRL, null))
        .isInstanceOfSatisfying(
            InvalidInputException.class, error -> assertThat(error.field()).isEqualTo("faceValue"));
  }
}
