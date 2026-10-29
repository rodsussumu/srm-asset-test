package com.srm.credit.service;

import com.srm.credit.config.BusinessMetrics;
import com.srm.credit.dto.CreateSettlementRequest;
import com.srm.credit.exception.InvalidInputException;
import com.srm.credit.exception.ResourceNotFoundException;
import com.srm.credit.exception.SettlementConflictException;
import com.srm.credit.exception.SettlementConflictException.Code;
import com.srm.credit.exception.SettlementPersistenceException;
import com.srm.credit.model.Settlement;
import com.srm.credit.model.SettlementIntent;
import com.srm.credit.model.StoredSettlementResponse;
import com.srm.credit.repository.SettlementRepository;
import java.sql.SQLException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettlementService {
  private static final Logger LOG = LoggerFactory.getLogger(SettlementService.class);
  private final SettlementTransactionService transaction;
  private final SettlementRepository repository;
  private final BusinessMetrics metrics;

  public SettlementService(
      SettlementTransactionService transaction,
      SettlementRepository repository,
      BusinessMetrics metrics) {
    this.transaction = transaction;
    this.repository = repository;
    this.metrics = metrics;
  }

  public StoredSettlementResponse confirm(
      String actor, String keyText, CreateSettlementRequest request, UUID correlationId) {
    if (actor == null || actor.isBlank() || actor.length() > 200) {
      throw new InvalidInputException("actor", "Identidade do operador inválida.");
    }
    UUID key = SettlementIntent.requireKey(keyText);
    var intent =
        new SettlementIntent(
            request.receivableId(),
            request.currency(),
            request.expectedPaymentAmount(),
            request.expectedFxRateId());
    StoredSettlementResponse result;
    try {
      result = transaction.confirm(actor, key, intent, intent.fingerprint(), correlationId);
    } catch (OptimisticLockingFailureException conflict) {
      throw new SettlementConflictException(Code.RECEIVABLE_ALREADY_SETTLED);
    } catch (DataIntegrityViolationException failure) {
      for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
        if (cause instanceof SQLException sql
            && "23505".equals(sql.getSQLState())
            && sql.getMessage().contains("uk_settlements_receivable")) {
          throw new SettlementConflictException(Code.RECEIVABLE_ALREADY_SETTLED);
        }
      }
      throw new SettlementPersistenceException(failure);
    }
    // The transaction proxy has committed before returning here. Replays are not new confirmations.
    if (!result.replay()) {
      metrics.confirmed(intent.currency());
      LOG.atInfo()
          .addKeyValue("event", "settlement_confirmed")
          .addKeyValue("actorId", actor)
          .addKeyValue("receivableId", intent.receivableId())
          .addKeyValue("settlementId", result.settlementId())
          .addKeyValue("outcome", "success")
          .log("Liquidação confirmada após commit.");
    }
    return result;
  }

  @Transactional(readOnly = true)
  public Settlement find(UUID id) {
    return repository.find(id).orElseThrow(() -> new ResourceNotFoundException("Liquidação"));
  }
}
