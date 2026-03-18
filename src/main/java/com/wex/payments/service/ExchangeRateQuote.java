package com.wex.payments.service;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExchangeRateQuote(
        String countryCurrency,
        LocalDate exchangeRateDate,
        BigDecimal exchangeRate
) {
}
