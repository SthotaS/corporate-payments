package com.wex.payments.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.wex.payments.constants.TreasuryConstants;
import com.wex.payments.constants.ValidationConstants;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreatePurchaseTransactionRequest(
        @NotBlank(message = ValidationConstants.DESCRIPTION_REQUIRED_MESSAGE)
        @Size(max = 50, message = ValidationConstants.DESCRIPTION_MAX_LENGTH_MESSAGE)
        String description,

        @NotNull(message = ValidationConstants.TRANSACTION_DATE_REQUIRED_MESSAGE)
        @PastOrPresent(message = ValidationConstants.TRANSACTION_DATE_FUTURE_MESSAGE)
        @JsonFormat(pattern = TreasuryConstants.API_DATE_PATTERN)
        LocalDate transactionDate,

        @NotNull(message = ValidationConstants.PURCHASE_AMOUNT_REQUIRED_MESSAGE)
        @DecimalMin(value = "0.01", inclusive = true, message = ValidationConstants.PURCHASE_AMOUNT_POSITIVE_MESSAGE)
        BigDecimal purchaseAmount
) {
    public static final LocalDate MIN_SUPPORTED_TREASURY_DATE = TreasuryConstants.MIN_SUPPORTED_TREASURY_DATE;
}
