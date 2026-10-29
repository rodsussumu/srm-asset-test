package com.srm.credit.model.strategy;

import com.srm.credit.model.ReceivableType;
import java.math.BigDecimal;

public interface SpreadStrategy {
  ReceivableType type();

  BigDecimal monthlySpread();
}
