package com.wex.payments.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PurchaseTransactionResponse(
        UUID id,
        String description,
        LocalDate transactionDate,
        BigDecimal purchaseAmountUsd
) {
}
