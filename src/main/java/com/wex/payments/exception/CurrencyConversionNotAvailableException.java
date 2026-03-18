package com.wex.payments.exception;

import java.time.LocalDate;

public class CurrencyConversionNotAvailableException extends RuntimeException {

    public CurrencyConversionNotAvailableException(String targetCurrency, LocalDate purchaseDate) {
        super("purchase dated %s cannot be converted to '%s' because no exchange rate exists on or before that date within the last 6 months"
                .formatted(purchaseDate, targetCurrency));
    }
}
