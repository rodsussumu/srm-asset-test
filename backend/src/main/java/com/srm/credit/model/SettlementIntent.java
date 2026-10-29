package com.srm.credit.model;

import com.srm.credit.exception.InvalidInputException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

public record SettlementIntent(
    UUID receivableId,
    PaymentCurrency currency,
    String expectedPaymentAmount,
    UUID expectedFxRateId) {
  public static final String ROUTE = "/api/v1/settlements";

  public SettlementIntent {
    if (receivableId == null || currency == null) {
      throw new InvalidInputException("body", "Recebível e moeda são obrigatórios.");
    }
    if (expectedPaymentAmount == null
        || !expectedPaymentAmount.matches("(?:0|[1-9][0-9]{0,27})(?:\\.[0-9]{1,2})?")) {
      throw new InvalidInputException(
          "expectedPaymentAmount",
          "Informe string decimal não negativa com até 28 dígitos inteiros e duas casas.");
    }
    expectedPaymentAmount = new BigDecimal(expectedPaymentAmount).setScale(2).toPlainString();
    if (currency == PaymentCurrency.USD && expectedFxRateId == null) {
      throw new InvalidInputException(
          "expectedFxRateId", "Cotação confirmada obrigatória para USD.");
    }
    if (currency == PaymentCurrency.BRL && expectedFxRateId != null) {
      throw new InvalidInputException("expectedFxRateId", "Pagamento em BRL não recebe cotação.");
    }
  }

  public String fingerprint() {
    String canonical =
        receivableId + "\n" + currency + "\n" + expectedPaymentAmount + "\n" + expectedFxRateId;
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(canonical.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  public static UUID requireKey(String value) {
    try {
      UUID key = UUID.fromString(value);
      if (!key.toString().equalsIgnoreCase(value)) {
        throw new IllegalArgumentException();
      }
      return key;
    } catch (IllegalArgumentException | NullPointerException invalid) {
      throw new InvalidInputException("Idempotency-Key", "Informe uma chave UUID canônica.");
    }
  }
}
