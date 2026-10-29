package com.srm.credit.service;

import com.srm.credit.config.BusinessMetrics;
import com.srm.credit.dto.FxRateResponse;
import com.srm.credit.dto.PricingSimulationRequest;
import com.srm.credit.dto.PricingSimulationResponse;
import com.srm.credit.model.FinancialAmounts;
import com.srm.credit.model.MonthlyTermPolicy;
import com.srm.credit.model.PaymentCurrency;
import com.srm.credit.model.PricingEngine;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PricingSimulationService {
  private final PricingEngine engine;
  private final BusinessMetrics metrics;
  private final MonthlyTermPolicy termPolicy;
  private final FxRateService rates;
  private final Clock clock;

  public PricingSimulationService(
      PricingEngine engine,
      MonthlyTermPolicy termPolicy,
      FxRateService rates,
      Clock clock,
      BusinessMetrics metrics) {
    this.engine = engine;
    this.metrics = metrics;
    this.termPolicy = termPolicy;
    this.rates = rates;
    this.clock = clock;
  }

  public PricingSimulationResponse simulate(PricingSimulationRequest request) {
    var operationAt = clock.instant();
    var face = FinancialAmounts.parseFace(request.faceValue());
    var term = termPolicy.evaluate(request.dueDate(), operationAt);
    var fx = request.paymentCurrency() == PaymentCurrency.USD ? rates.selectAt(operationAt) : null;
    var result =
        metrics.price(
            request.paymentCurrency(),
            () ->
                engine.calculate(
                    face,
                    request.type(),
                    term.months(),
                    request.paymentCurrency(),
                    fx == null ? null : fx.getRate()));
    return new PricingSimulationResponse(
        true,
        face.toPlainString(),
        "BRL",
        request.dueDate(),
        request.type(),
        result.presentValueBrl().toPlainString(),
        result.discountBrl().toPlainString(),
        result.paymentAmount().toPlainString(),
        result.paymentCurrency(),
        result.months(),
        result.baseRate().toPlainString(),
        result.spread().toPlainString(),
        operationAt,
        term.operationDate(),
        MonthlyTermPolicy.BUSINESS_ZONE.getId(),
        "ceil(calendarDays/30)",
        "HALF_EVEN",
        result.policyVersion(),
        fx == null ? null : FxRateResponse.from(fx));
  }
}
