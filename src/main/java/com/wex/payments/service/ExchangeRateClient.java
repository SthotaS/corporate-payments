package com.wex.payments.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.wex.payments.constants.TreasuryConstants;
import com.wex.payments.exception.UpstreamExchangeRateException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
public class ExchangeRateClient {

    private static final DateTimeFormatter API_DATE_FORMAT = DateTimeFormatter.ofPattern(TreasuryConstants.API_DATE_PATTERN, Locale.US);

    private final RestClient restClient;
    private final String ratesOfExchangePath;

    public ExchangeRateClient(RestClient treasuryRestClient,
                              @Value("${treasury.api.rates-of-exchange-path}") String ratesOfExchangePath) {
        this.restClient = treasuryRestClient;
        this.ratesOfExchangePath = normalizeRelativePath(ratesOfExchangePath);
    }

    public Optional<ExchangeRateQuote> findExchangeRate(String countryCurrency, LocalDate purchaseDate) {
        LocalDate lowerBound = purchaseDate.minusMonths(6);
        String filter = buildFilter(countryCurrency, lowerBound, purchaseDate);
        JsonNode response;

        try {
            response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(ratesOfExchangePath)
                            .queryParam("fields", "country_currency_desc,exchange_rate,record_date")
                            .queryParam("filter", filter)
                            .queryParam("sort", "-record_date")
                            .queryParam("page[size]", 200)
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException exception) {
            throw new UpstreamExchangeRateException(TreasuryConstants.REQUEST_FAILED_MESSAGE, exception);
        }

        if (response == null || !response.has("data") || !response.get("data").isArray()) {
            throw new UpstreamExchangeRateException(TreasuryConstants.UNEXPECTED_RESPONSE_MESSAGE);
        }

        List<ExchangeRateQuote> quotes = new ArrayList<>();
        for (JsonNode node : response.get("data")) {
            LocalDate recordDate = parseRecordDate(node.path("record_date").asText(null));
            if (recordDate == null) {
                continue;
            }

            if (recordDate.isAfter(purchaseDate) || recordDate.isBefore(lowerBound)) {
                continue;
            }

            String recordCountryCurrency = node.path("country_currency_desc").asText(null);
            BigDecimal exchangeRate = parseExchangeRate(node.path("exchange_rate").asText(null));
            if (!StringUtils.hasText(recordCountryCurrency) || exchangeRate == null) {
                continue;
            }

            quotes.add(new ExchangeRateQuote(recordCountryCurrency, recordDate, exchangeRate));
        }

        return quotes.stream()
                .max(Comparator.comparing(ExchangeRateQuote::exchangeRateDate));
    }

    private String buildFilter(String countryCurrency, LocalDate fromDate, LocalDate toDate) {
        return "country_currency_desc:eq:%s,record_date:gte:%s,record_date:lte:%s"
                .formatted(countryCurrency, API_DATE_FORMAT.format(fromDate), API_DATE_FORMAT.format(toDate));
    }

    private String normalizeRelativePath(String configuredPath) {
        if (!StringUtils.hasText(configuredPath)) {
            throw new IllegalArgumentException("treasury.api.rates-of-exchange-path must not be blank");
        }

        return configuredPath.startsWith("/") ? configuredPath : "/" + configuredPath;
    }

    private LocalDate parseRecordDate(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }

        try {
            return LocalDate.parse(rawValue, API_DATE_FORMAT);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private BigDecimal parseExchangeRate(String rawValue) {
        if (!StringUtils.hasText(rawValue) || "null".equalsIgnoreCase(rawValue)) {
            return null;
        }

        try {
            return new BigDecimal(rawValue.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
