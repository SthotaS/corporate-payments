package com.wex.payments.exception;

import java.util.UUID;

public class PurchaseNotFoundException extends RuntimeException {

    public PurchaseNotFoundException(UUID purchaseId) {
        super("purchase with id '%s' was not found".formatted(purchaseId));
    }
}
