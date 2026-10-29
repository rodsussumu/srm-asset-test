package com.srm.credit.model;

import com.srm.credit.exception.InvalidInputException;
import com.srm.credit.model.strategy.SpreadStrategy;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class PricingEngine {
  public static final String POLICY_VERSION = "monthly-ceil-30d-v1";
  private static final MathContext PRECISION = new MathContext(50, RoundingMode.HALF_EVEN);
  private final BigDecimal baseRate;
  private final Map<ReceivableType, SpreadStrategy> strategies =
      new EnumMap<>(ReceivableType.class);

  public PricingEngine(BigDecimal baseRate, List<SpreadStrategy> strategies) {
    if (baseRate == null
        || baseRate.signum() < 0
        || baseRate.scale() > 8
        || baseRate.compareTo(new BigDecimal("100")) >= 0) {
      throw new IllegalArgumentException("Taxa base inválida.");
    }
    this.baseRate = baseRate;
    for (SpreadStrategy strategy : strategies) {
      if (this.strategies.put(strategy.type(), strategy) != null) {
        throw new IllegalArgumentException("Strategy duplicada: " + strategy.type());
      }
    }
  }

  public PricingResult calculate(
      BigDecimal face,
      ReceivableType type,
      int months,
      PaymentCurrency currency,
      BigDecimal fxRate) {
    face = FinancialAmounts.requireFace(face);
    if (months < 0 || months > 36) {
      throw new InvalidInputException("months", "Prazo deve estar entre zero e 36 meses.");
    }
    if (currency == null) {
      throw new InvalidInputException("paymentCurrency", "Moeda de pagamento obrigatória.");
    }
    SpreadStrategy strategy = type == null ? null : strategies.get(type);
    if (strategy == null) {
      throw new InvalidInputException("type", "Tipo de recebível sem estratégia cadastrada.");
    }
    BigDecimal spread = strategy.monthlySpread();
    // A potência inteira é exata; a divisão usa 50 dígitos antes do arredondamento monetário.
    BigDecimal factor = BigDecimal.ONE.add(baseRate).add(spread).pow(months);
    BigDecimal pvBrl = FinancialAmounts.roundMoney(face.divide(factor, PRECISION));
    BigDecimal payment = pvBrl;
    BigDecimal usedFx = null;
    if (currency == PaymentCurrency.USD) {
      usedFx = FinancialAmounts.requireFxRate(fxRate);
      payment = FinancialAmounts.roundMoney(pvBrl.divide(usedFx, PRECISION));
    }
    return new PricingResult(
        pvBrl,
        face.subtract(pvBrl),
        payment,
        currency,
        months,
        baseRate,
        spread,
        usedFx,
        POLICY_VERSION);
  }
}
