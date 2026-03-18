package com.wex.payments.exception;

public class UpstreamExchangeRateException extends RuntimeException {

    public UpstreamExchangeRateException(String message) {
        super(message);
    }

    public UpstreamExchangeRateException(String message, Throwable cause) {
        super(message, cause);
    }
}
