package com.wex.payments.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.wex.payments.constants.TreasuryConstants;
import com.wex.payments.exception.UpstreamExchangeRateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger log = LoggerFactory.getLogger(ExchangeRateClient.class);

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

        log.info("requesting exchange rates targetCurrency={} purchaseDate={} lowerBound={} filter={}",
                countryCurrency,
                purchaseDate,
                lowerBound,
                filter);

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
            log.error("treasury exchange rate request failed targetCurrency={} purchaseDate={}",
                    countryCurrency,
                    purchaseDate,
                    exception);
            throw new UpstreamExchangeRateException(TreasuryConstants.REQUEST_FAILED_MESSAGE, exception);
        }

        if (response == null || !response.has("data") || !response.get("data").isArray()) {
            log.error("treasury exchange rate response was malformed targetCurrency={} purchaseDate={}",
                    countryCurrency,
                    purchaseDate);
            throw new UpstreamExchangeRateException(TreasuryConstants.UNEXPECTED_RESPONSE_MESSAGE);
        }

        List<ExchangeRateQuote> quotes = new ArrayList<>();
        for (JsonNode node : response.get("data")) {
            String rawRecordDate = node.path("record_date").asText(null);
            String rawCountryCurrency = node.path("country_currency_desc").asText(null);
            String rawExchangeRate = node.path("exchange_rate").asText(null);

            log.debug("evaluating treasury row countryCurrency={} recordDate={} exchangeRate={}",
                    rawCountryCurrency,
                    rawRecordDate,
                    rawExchangeRate);

            LocalDate recordDate = parseRecordDate(rawRecordDate);
            if (recordDate == null) {
                log.debug("skipping treasury row because recordDate could not be parsed rawRecordDate={}", rawRecordDate);
                continue;
            }

            if (recordDate.isAfter(purchaseDate) || recordDate.isBefore(lowerBound)) {
                log.debug("skipping treasury row because recordDate={} is outside supported range {} to {}",
                        recordDate,
                        lowerBound,
                        purchaseDate);
                continue;
            }

            String recordCountryCurrency = rawCountryCurrency;
            BigDecimal exchangeRate = parseExchangeRate(rawExchangeRate);
            if (!StringUtils.hasText(recordCountryCurrency) || exchangeRate == null) {
                log.debug("skipping treasury row because currency or exchange rate is invalid countryCurrency={} exchangeRate={}",
                        recordCountryCurrency,
                        rawExchangeRate);
                continue;
            }

            quotes.add(new ExchangeRateQuote(recordCountryCurrency, recordDate, exchangeRate));
        }

        log.debug("treasury rows matched after filtering count={}", quotes.size());

        Optional<ExchangeRateQuote> selectedQuote = quotes.stream()
                .max(Comparator.comparing(ExchangeRateQuote::exchangeRateDate));

        if (selectedQuote.isPresent()) {
            ExchangeRateQuote quote = selectedQuote.get();
            log.info("selected exchange rate targetCurrency={} exchangeRateDate={} exchangeRate={}",
                    quote.countryCurrency(),
                    quote.exchangeRateDate(),
                    quote.exchangeRate());
        } else {
            log.warn("no exchange rate found targetCurrency={} purchaseDate={} lowerBound={}",
                    countryCurrency,
                    purchaseDate,
                    lowerBound);
        }

        return selectedQuote;
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
