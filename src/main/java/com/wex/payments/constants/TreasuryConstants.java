package com.wex.payments.constants;

import java.time.LocalDate;

public final class TreasuryConstants {

    public static final String API_DATE_PATTERN = "yyyy-MM-dd";
    public static final LocalDate MIN_SUPPORTED_TREASURY_DATE = LocalDate.of(2001, 3, 31);
    public static final String REQUEST_FAILED_MESSAGE = "Treasury exchange-rate API request failed";
    public static final String UNEXPECTED_RESPONSE_MESSAGE = "Treasury exchange-rate API returned an unexpected response";
    public static final String MIN_SUPPORTED_DATE_MESSAGE = "must be on or after 2001-03-31";
    public static final int DEFAULT_MAX_ATTEMPTS = 3;

    private TreasuryConstants() {
    }
}
