package com.srm.credit.model;

import com.srm.credit.exception.InvalidInputException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public record StatementQuery(
    Instant from, Instant to, UUID assignorId, PaymentCurrency currency, int page, int size) {
  public StatementQuery {
    if (from == null) {
      throw new InvalidInputException("from", "Início do período obrigatório.");
    }
    if (to == null) {
      throw new InvalidInputException("to", "Fim do período obrigatório.");
    }
    if (!to.isAfter(from) || Duration.between(from, to).compareTo(Duration.ofDays(366)) > 0) {
      throw new InvalidInputException(
          "to", "Fim deve suceder o início; período máximo de 366 dias.");
    }
    if (page < 0) {
      throw new InvalidInputException("page", "Página deve ser maior ou igual a zero.");
    }
    if (size < 1 || size > 100) {
      throw new InvalidInputException("size", "Tamanho deve estar entre 1 e 100.");
    }
  }

  public long offset() {
    return (long) page * size;
  }
}
