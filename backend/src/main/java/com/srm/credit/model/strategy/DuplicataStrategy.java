package com.srm.credit.model.strategy;

import com.srm.credit.model.ReceivableType;
import java.math.BigDecimal;

public final class DuplicataStrategy implements SpreadStrategy {
  @Override
  public ReceivableType type() {
    return ReceivableType.DUPLICATA;
  }

  @Override
  public BigDecimal monthlySpread() {
    return new BigDecimal("0.0150");
  }
}
