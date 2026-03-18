package com.wex.payments.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component("treasuryApi")
public class TreasuryApiHealthIndicator implements HealthIndicator {

    private final RestClient treasuryRestClient;
    private final String ratesOfExchangePath;

    public TreasuryApiHealthIndicator(RestClient treasuryRestClient,
                                      @Value("${treasury.api.rates-of-exchange-path}") String ratesOfExchangePath) {
        this.treasuryRestClient = treasuryRestClient;
        this.ratesOfExchangePath = normalizeRelativePath(ratesOfExchangePath);
    }

    @Override
    public Health health() {
        try {
            HttpStatusCode statusCode = treasuryRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(ratesOfExchangePath)
                            .queryParam("fields", "record_date")
                            .queryParam("page[size]", 1)
                            .build())
                    .retrieve()
                    .toBodilessEntity()
                    .getStatusCode();

            if (statusCode.is2xxSuccessful()) {
                return Health.up()
                        .withDetail("upstream", "Treasury Reporting Rates API")
                        .withDetail("statusCode", statusCode.value())
                        .build();
            }

            return Health.down()
                    .withDetail("upstream", "Treasury Reporting Rates API")
                    .withDetail("statusCode", statusCode.value())
                    .build();
        } catch (RestClientException exception) {
            return Health.down(exception)
                    .withDetail("upstream", "Treasury Reporting Rates API")
                    .build();
        }
    }

    private String normalizeRelativePath(String configuredPath) {
        if (!StringUtils.hasText(configuredPath)) {
            throw new IllegalArgumentException("treasury.api.rates-of-exchange-path must not be blank");
        }

        return configuredPath.startsWith("/") ? configuredPath : "/" + configuredPath;
    }
}
