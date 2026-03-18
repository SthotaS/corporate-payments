package com.wex.payments.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ConvertedPurchaseTransactionResponse(
        UUID id,
        String description,
        LocalDate transactionDate,
        BigDecimal originalPurchaseAmountUsd,
        String countryCurrency,
        LocalDate exchangeRateDate,
        BigDecimal exchangeRate,
        BigDecimal convertedAmount
) {
}
