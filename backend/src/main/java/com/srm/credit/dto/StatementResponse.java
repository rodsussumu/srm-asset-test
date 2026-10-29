package com.srm.credit.dto;

import java.util.List;

public record StatementResponse(
    List<SettlementResponse> items,
    int page,
    int size,
    long total,
    List<StatementCurrencyTotal> totalsByCurrency) {}
