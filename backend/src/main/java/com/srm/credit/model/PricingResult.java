package com.srm.credit.model;

import java.math.BigDecimal;

public record PricingResult(
    BigDecimal presentValueBrl,
    BigDecimal discountBrl,
    BigDecimal paymentAmount,
    PaymentCurrency paymentCurrency,
    int months,
    BigDecimal baseRate,
    BigDecimal spread,
    BigDecimal fxRate,
    String policyVersion) {}
