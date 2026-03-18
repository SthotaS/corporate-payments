package com.wex.payments.exception;

public class InvalidCountryCurrencyException extends RuntimeException {

    public InvalidCountryCurrencyException() {
        super("countryCurrency: countryCurrency must match Treasury country-currency format");
    }
}
