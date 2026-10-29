package com.srm.credit.config;

import com.srm.credit.model.MonthlyTermPolicy;
import com.srm.credit.model.PricingEngine;
import com.srm.credit.model.strategy.ChequeStrategy;
import com.srm.credit.model.strategy.DuplicataStrategy;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BusinessConfig {
  @Bean
  PricingEngine pricingEngine(@Value("${pricing.base-rate}") BigDecimal baseRate) {
    return new PricingEngine(baseRate, List.of(new DuplicataStrategy(), new ChequeStrategy()));
  }

  @Bean
  MonthlyTermPolicy monthlyTermPolicy(Clock clock) {
    return new MonthlyTermPolicy(clock);
  }
}
