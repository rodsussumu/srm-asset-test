package com.srm.credit.model;

import java.math.BigDecimal;

public record CurrencyTotal(PaymentCurrency currency, long count, BigDecimal paymentTotal) {}
