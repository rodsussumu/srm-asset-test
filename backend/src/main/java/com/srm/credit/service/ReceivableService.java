package com.srm.credit.service;

import com.srm.credit.exception.InvalidInputException;
import com.srm.credit.exception.ResourceNotFoundException;
import com.srm.credit.model.FinancialAmounts;
import com.srm.credit.model.MonthlyTermPolicy;
import com.srm.credit.model.PaymentCurrency;
import com.srm.credit.model.Receivable;
import com.srm.credit.model.ReceivableStatus;
import com.srm.credit.model.ReceivableType;
import com.srm.credit.repository.AssignorRepository;
import com.srm.credit.repository.ReceivableRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReceivableService {
  private final ReceivableRepository repository;
  private final AssignorRepository assignors;
  private final MonthlyTermPolicy termPolicy;
  private final Clock clock;

  public ReceivableService(
      ReceivableRepository repository,
      AssignorRepository assignors,
      MonthlyTermPolicy termPolicy,
      Clock clock) {
    this.repository = repository;
    this.assignors = assignors;
    this.termPolicy = termPolicy;
    this.clock = clock;
  }

  @Transactional
  public Receivable create(
      UUID assignorId,
      String faceValue,
      LocalDate dueDate,
      ReceivableType type,
      PaymentCurrency paymentCurrency) {
    var face = FinancialAmounts.parseFace(faceValue);
    termPolicy.evaluate(dueDate);
    if (assignorId == null || type == null || paymentCurrency == null) {
      throw new InvalidInputException("body", "Cedente, tipo e moeda são obrigatórios.");
    }
    var assignor =
        assignors
            .findById(assignorId)
            .orElseThrow(() -> new InvalidInputException("assignorId", "Cedente não existe."));
    return repository.saveAndFlush(
        new Receivable(assignor, face, dueDate, type, paymentCurrency, clock.instant()));
  }

  public Receivable find(UUID id) {
    return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Recebível"));
  }

  public Page<Receivable> list(int page, int size, ReceivableStatus status) {
    var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    return status == null
        ? repository.findAll(pageable)
        : repository.findByStatus(status, pageable);
  }
}
