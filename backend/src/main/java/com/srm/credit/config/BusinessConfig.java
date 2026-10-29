package com.srm.credit.config;

import com.srm.credit.model.MonthlyTermPolicy;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BusinessConfig {
  @Bean
  MonthlyTermPolicy monthlyTermPolicy(Clock clock) {
    return new MonthlyTermPolicy(clock);
  }
}
