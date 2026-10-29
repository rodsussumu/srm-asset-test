package com.srm.credit;

import static org.assertj.core.api.Assertions.*;

import com.srm.credit.config.BusinessMetrics;
import com.srm.credit.model.PaymentCurrency;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class BusinessMetricsTest {
  @Test
  void recordsFailedCalculationsWithoutHidingFailureAndUsesBoundedDimensions() {
    var registry = new SimpleMeterRegistry();
    try {
      var metrics = new BusinessMetrics(registry);
      assertThatThrownBy(
              () ->
                  metrics.price(
                      PaymentCurrency.USD,
                      () -> {
                        throw new IllegalArgumentException("injected");
                      }))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("injected");
      var timer =
          registry.get("pricing.duration").tags("currency", "USD", "outcome", "failure").timer();
      assertThat(timer.count()).isEqualTo(1);
      assertThat(timer.totalTime(TimeUnit.NANOSECONDS)).isPositive();
      assertThat(registry.getMeters())
          .allSatisfy(
              meter -> {
                assertThat(meter.getId().getTags())
                    .allSatisfy(tag -> assertThat(tag.getKey()).isIn("currency", "outcome"));
              });
    } finally {
      registry.close();
    }
  }
}
