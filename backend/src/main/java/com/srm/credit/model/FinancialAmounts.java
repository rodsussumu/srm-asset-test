package com.srm.credit.model;

import com.srm.credit.exception.InvalidInputException;
import java.math.BigDecimal;
import java.math.RoundingMode;

public final class FinancialAmounts {
  private static final BigDecimal MAX_FACE = new BigDecimal("9999999999999999.99");
  private static final BigDecimal MAX_FX_RATE = new BigDecimal("99999999.99999999");

  private FinancialAmounts() {}

  public static BigDecimal parseFace(String text) {
    if (text == null || !text.matches("(?:0|[1-9][0-9]{0,15})(?:\\.[0-9]{1,2})?")) {
      throw new InvalidInputException(
          "faceValue", "Informe um decimal positivo com até duas casas.");
    }
    return requireFace(new BigDecimal(text));
  }

  public static BigDecimal requireFace(BigDecimal value) {
    if (value == null
        || value.signum() <= 0
        || value.scale() > 2
        || value.compareTo(MAX_FACE) > 0) {
      throw new InvalidInputException("faceValue", "Valor de face fora dos limites permitidos.");
    }
    return value.setScale(2, RoundingMode.UNNECESSARY);
  }

  public static BigDecimal requireFxRate(BigDecimal value) {
    if (value == null
        || value.signum() <= 0
        || value.scale() > 8
        || value.compareTo(MAX_FX_RATE) > 0) {
      throw new InvalidInputException("fxRate", "Informe uma cotação positiva com até oito casas.");
    }
    return value;
  }

  public static BigDecimal parseFxRate(String text) {
    if (text == null || !text.matches("(?:0|[1-9][0-9]{0,7})(?:\\.[0-9]{1,8})?")) {
      throw new InvalidInputException(
          "rate", "Informe uma string decimal positiva com até oito casas.");
    }
    BigDecimal value = new BigDecimal(text);
    if (value.signum() <= 0) {
      throw new InvalidInputException("rate", "Informe uma cotação positiva.");
    }
    return requireFxRate(value);
  }

  public static BigDecimal roundMoney(BigDecimal value) {
    return value.setScale(2, RoundingMode.HALF_EVEN);
  }
}
