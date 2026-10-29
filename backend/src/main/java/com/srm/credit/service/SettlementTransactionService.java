package com.srm.credit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.srm.credit.dto.SettlementResponse;
import com.srm.credit.exception.InvalidInputException;
import com.srm.credit.exception.ResourceNotFoundException;
import com.srm.credit.exception.SettlementConflictException;
import com.srm.credit.exception.SettlementConflictException.Code;
import com.srm.credit.model.*;
import com.srm.credit.repository.IdempotencyRepository;
import com.srm.credit.repository.ReceivableRepository;
import com.srm.credit.repository.SettlementRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettlementTransactionService {
  private final IdempotencyRepository idempotency;
  private final ReceivableRepository receivables;
  private final SettlementRepository settlements;
  private final FxRateService rates;
  private final PricingEngine engine;
  private final MonthlyTermPolicy policy;
  private final Clock clock;
  private final ObjectMapper mapper;

  public SettlementTransactionService(
      IdempotencyRepository idempotency,
      ReceivableRepository receivables,
      SettlementRepository settlements,
      FxRateService rates,
      PricingEngine engine,
      MonthlyTermPolicy policy,
      Clock clock,
      ObjectMapper mapper) {
    this.idempotency = idempotency;
    this.receivables = receivables;
    this.settlements = settlements;
    this.rates = rates;
    this.engine = engine;
    this.policy = policy;
    this.clock = clock;
    this.mapper = mapper;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
  public StoredSettlementResponse confirm(
      String actor, UUID key, SettlementIntent intent, String hash, UUID correlationId) {
    if (!idempotency.reserve(actor, key, hash, clock.instant())) {
      var existing = idempotency.find(actor, key);
      if (!existing.hash().equals(hash)) {
        throw new SettlementConflictException(Code.IDEMPOTENCY_KEY_REUSED);
      }
      return existing.response();
    }
    var operationAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
    var receivable =
        receivables
            .findById(intent.receivableId())
            .orElseThrow(() -> new ResourceNotFoundException("Recebível"));
    if (receivable.getStatus() != ReceivableStatus.AVAILABLE) {
      throw new SettlementConflictException(Code.RECEIVABLE_ALREADY_SETTLED);
    }
    if (receivable.getPaymentCurrency() != intent.currency()) {
      throw new InvalidInputException(
          "currency", "Moeda deve coincidir com o recebível cadastrado.");
    }
    var term = policy.evaluate(receivable.getDueDate(), operationAt);
    var fx = intent.currency() == PaymentCurrency.USD ? rates.selectAt(operationAt) : null;
    var price =
        engine.calculate(
            receivable.getFaceValue(),
            receivable.getType(),
            term.months(),
            intent.currency(),
            fx == null ? null : fx.getRate());
    if (price.paymentAmount().compareTo(new BigDecimal(intent.expectedPaymentAmount())) != 0
        || (fx != null && !fx.getId().equals(intent.expectedFxRateId()))) {
      throw new SettlementConflictException(Code.PRICE_CHANGED);
    }
    receivable.markSettled();
    receivables.flush();
    var settlement =
        new Settlement(
            UUID.randomUUID(),
            receivable.getId(),
            receivable.getAssignor().getId(),
            receivable.getAssignor().getName(),
            actor,
            receivable.getFaceValue(),
            receivable.getDueDate(),
            receivable.getType(),
            term.months(),
            term.operationDate(),
            operationAt,
            price.baseRate(),
            price.spread(),
            price.presentValueBrl(),
            price.discountBrl(),
            price.paymentCurrency(),
            price.paymentAmount(),
            fx == null ? null : fx.getId(),
            fx == null ? null : fx.getRate(),
            fx == null ? null : fx.getOrigin(),
            fx == null ? null : fx.getEffectiveAt(),
            fx == null ? null : fx.getRecordedAt(),
            price.policyVersion(),
            correlationId,
            operationAt,
            receivable.getVersion());
    settlements.save(settlement);
    String body;
    try {
      body = mapper.writeValueAsString(SettlementResponse.from(settlement));
    } catch (JsonProcessingException failure) {
      throw new IllegalStateException("Não foi possível serializar a confirmação.", failure);
    }
    var response =
        new StoredSettlementResponse(
            201, body, SettlementIntent.ROUTE + "/" + settlement.id(), settlement.id(), false);
    idempotency.complete(actor, key, response, operationAt);
    return response;
  }
}
