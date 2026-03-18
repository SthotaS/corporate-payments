package com.wex.payments.service;

import com.wex.payments.exception.UpstreamExchangeRateException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpStatus.NOT_FOUND;

class ExchangeRateClientTest {

    private MockRestServiceServer server;
    private ExchangeRateClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.fiscaldata.treasury.gov/services/api/fiscal_service");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new ExchangeRateClient(builder.build(), "/v1/accounting/od/rates_of_exchange");
    }

    @Test
    void findsLatestRateOnOrBeforePurchaseDateWithinSixMonths() {
        server.expect(requestTo(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.startsWith("https://api.fiscaldata.treasury.gov/services/api/fiscal_service/v1/accounting/od/rates_of_exchange"),
                        org.hamcrest.Matchers.containsString("country_currency_desc:eq:Canada-Dollar"),
                        org.hamcrest.Matchers.containsString("record_date:gte:2025-09-15"),
                        org.hamcrest.Matchers.containsString("record_date:lte:2026-03-15"))))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {
                          "data": [
                            {
                              "country_currency_desc": "Canada-Dollar",
                              "exchange_rate": "1.3500",
                              "record_date": "2025-11-30"
                            },
                            {
                              "country_currency_desc": "Canada-Dollar",
                              "exchange_rate": "1.4000",
                              "record_date": "2025-12-31"
                            },
                            {
                              "country_currency_desc": "Canada-Dollar",
                              "exchange_rate": "1.5000",
                              "record_date": "2026-04-30"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        ExchangeRateQuote quote = client.findExchangeRate("Canada-Dollar", LocalDate.of(2026, 3, 15)).orElseThrow();

        assertThat(quote.exchangeRateDate()).isEqualTo(LocalDate.of(2025, 12, 31));
        assertThat(quote.exchangeRate().toPlainString()).isEqualTo("1.4000");
    }

    @Test
    void returnsEmptyWhenNoRateExistsWithinSixMonths() {
        server.expect(requestTo(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.startsWith("https://api.fiscaldata.treasury.gov/services/api/fiscal_service/v1/accounting/od/rates_of_exchange"),
                        org.hamcrest.Matchers.containsString("country_currency_desc:eq:Canada-Dollar"))))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {
                          "data": [
                            {
                              "country_currency_desc": "Canada-Dollar",
                              "exchange_rate": "1.3500",
                              "record_date": "2025-07-31"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.findExchangeRate("Canada-Dollar", LocalDate.of(2026, 3, 15))).isEmpty();
    }

    @Test
    void wrapsUpstreamHttpErrors() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(
                        "https://api.fiscaldata.treasury.gov/services/api/fiscal_service/v1/accounting/od/rates_of_exchange")))
                .andExpect(method(GET))
                .andRespond(withStatus(NOT_FOUND));

        assertThatThrownBy(() -> client.findExchangeRate("Canada-Dollar", LocalDate.of(2026, 3, 15)))
                .isInstanceOf(UpstreamExchangeRateException.class)
                .hasMessage("Treasury exchange-rate API request failed");
    }
}
