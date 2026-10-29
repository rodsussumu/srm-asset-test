package com.srm.credit.dto;

import com.srm.credit.model.PaymentCurrency;

public record StatementCurrencyTotal(PaymentCurrency currency, long count, String paymentTotal) {}
