package com.srm.credit.service;

import com.srm.credit.dto.SettlementResponse;
import com.srm.credit.dto.StatementCurrencyTotal;
import com.srm.credit.dto.StatementResponse;
import com.srm.credit.model.StatementQuery;
import com.srm.credit.repository.StatementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StatementService {
  private final StatementRepository repository;

  public StatementService(StatementRepository repository) {
    this.repository = repository;
  }

  @Transactional(
      readOnly = true,
      isolation = Isolation.REPEATABLE_READ,
      propagation = Propagation.REQUIRES_NEW)
  public StatementResponse find(StatementQuery query) {
    var items = repository.list(query).stream().map(SettlementResponse::from).toList();
    long total = repository.count(query);
    var totals =
        repository.totals(query).stream()
            .map(
                value ->
                    new StatementCurrencyTotal(
                        value.currency(),
                        value.count(),
                        value.paymentTotal().setScale(2).toPlainString()))
            .toList();
    return new StatementResponse(items, query.page(), query.size(), total, totals);
  }
}
