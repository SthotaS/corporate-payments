package com.wex.payments.constants;

public final class ValidationConstants {

    public static final String DESCRIPTION_REQUIRED_MESSAGE = "description is required";
    public static final String DESCRIPTION_MAX_LENGTH_MESSAGE = "must not exceed 50 characters";
    public static final String TRANSACTION_DATE_REQUIRED_MESSAGE = "transactionDate is required";
    public static final String TRANSACTION_DATE_FUTURE_MESSAGE = "transactionDate must not be in the future";
    public static final String TRANSACTION_DATE_INVALID_FORMAT_DETAIL = "transactionDate: must be a valid date format";
    public static final String PURCHASE_AMOUNT_REQUIRED_MESSAGE = "purchaseAmount is required";
    public static final String PURCHASE_AMOUNT_POSITIVE_MESSAGE = "must be positive";
    public static final String COUNTRY_CURRENCY_REQUIRED_MESSAGE = "countryCurrency is required";
    public static final String COUNTRY_CURRENCY_INVALID_MESSAGE = "countryCurrency must match Treasury country-currency format";
    public static final String COUNTRY_CURRENCY_ALLOWED_PATTERN = "^[A-Za-z0-9 .()'&/]+-[A-Za-z0-9 .()'&/]+$";

    private ValidationConstants() {
    }
}
