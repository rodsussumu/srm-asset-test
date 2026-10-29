package com.srm.credit.config;

import com.srm.credit.model.PaymentCurrency;
import com.srm.credit.model.PricingResult;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class BusinessMetrics {
  private final MeterRegistry meters;

  public BusinessMetrics(MeterRegistry meters) {
    this.meters = meters;
    for (var currency : PaymentCurrency.values()) {
      meters.counter("settlements.confirmed", "currency", currency.name());
    }
  }

  public void confirmed(PaymentCurrency currency) {
    meters.counter("settlements.confirmed", "currency", currency.name()).increment();
  }

  public PricingResult price(PaymentCurrency currency, Supplier<PricingResult> calculation) {
    var sample = Timer.start(meters);
    String outcome = "failure";
    try {
      var result = calculation.get();
      outcome = "success";
      return result;
    } finally {
      sample.stop(
          meters.timer("pricing.duration", "currency", currency.name(), "outcome", outcome));
    }
  }
}
