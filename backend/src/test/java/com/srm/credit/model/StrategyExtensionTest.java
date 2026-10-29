package com.srm.credit.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.srm.credit.model.strategy.SpreadStrategy;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** A walkthrough fixture: alternative spread, with no new production type or duplicate formula. */
class StrategyExtensionTest {
  @Test
  void acceptsAnAlternativeStrategyWithoutChangingTheCalculationAlgorithm() {
    SpreadStrategy alternative =
        new SpreadStrategy() {
          @Override
          public ReceivableType type() {
            return ReceivableType.DUPLICATA;
          }

          @Override
          public BigDecimal monthlySpread() {
            return new BigDecimal("0.0200");
          }
        };
    var engine = new PricingEngine(new BigDecimal("0.0100"), List.of(alternative));
    var price =
        engine.calculate(
            new BigDecimal("100000.00"), ReceivableType.DUPLICATA, 3, PaymentCurrency.BRL, null);
    assertThat(price.presentValueBrl()).isEqualByComparingTo("91514.17");
    assertThat(price.discountBrl()).isEqualByComparingTo("8485.83");
    assertThat(price.spread()).isEqualByComparingTo("0.0200");
  }
}
