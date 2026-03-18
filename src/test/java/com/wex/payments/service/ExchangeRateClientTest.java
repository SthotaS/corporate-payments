package com.wex.payments.service;

import com.wex.payments.exception.UpstreamExchangeRateException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ExpectedCount;
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
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

class ExchangeRateClientTest {

    private MockRestServiceServer server;
    private ExchangeRateClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.fiscaldata.treasury.gov/services/api/fiscal_service");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new ExchangeRateClient(builder.build(), "/v1/accounting/od/rates_of_exchange", 3);
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
    void ignoresMalformedAndOutOfRangeRowsAndReturnsLatestEligibleQuote() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(
                        "https://api.fiscaldata.treasury.gov/services/api/fiscal_service/v1/accounting/od/rates_of_exchange")))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {
                          "data": [
                            {
                              "country_currency_desc": "Canada-Dollar",
                              "exchange_rate": "1.5000",
                              "record_date": "2026-04-30"
                            },
                            {
                              "country_currency_desc": "Canada-Dollar",
                              "exchange_rate": "1.1000",
                              "record_date": "not-a-date"
                            },
                            {
                              "country_currency_desc": "",
                              "exchange_rate": "1.1500",
                              "record_date": "2025-12-31"
                            },
                            {
                              "country_currency_desc": "Canada-Dollar",
                              "exchange_rate": "null",
                              "record_date": "2025-12-31"
                            },
                            {
                              "country_currency_desc": "Canada-Dollar",
                              "exchange_rate": "oops",
                              "record_date": "2025-12-15"
                            },
                            {
                              "country_currency_desc": "Canada-Dollar",
                              "exchange_rate": "1.3200",
                              "record_date": "2025-08-31"
                            },
                            {
                              "country_currency_desc": "Canada-Dollar",
                              "exchange_rate": "1.4000",
                              "record_date": "2025-12-31"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        ExchangeRateQuote quote = client.findExchangeRate("Canada-Dollar", LocalDate.of(2026, 3, 15)).orElseThrow();

        assertThat(quote.exchangeRateDate()).isEqualTo(LocalDate.of(2025, 12, 31));
        assertThat(quote.exchangeRate().toPlainString()).isEqualTo("1.4000");
    }

    @Test
    void throwsWhenTreasuryResponseDoesNotContainDataArray() {
        server.expect(ExpectedCount.times(3), requestTo(org.hamcrest.Matchers.startsWith(
                        "https://api.fiscaldata.treasury.gov/services/api/fiscal_service/v1/accounting/od/rates_of_exchange")))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {
                          "meta": {
                            "count": 0
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.findExchangeRate("Canada-Dollar", LocalDate.of(2026, 3, 15)))
                .isInstanceOf(UpstreamExchangeRateException.class)
                .hasMessage("Treasury exchange-rate API returned an unexpected response");
    }

    @Test
    void addsLeadingSlashWhenConfiguredPathDoesNotContainOne() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.fiscaldata.treasury.gov/services/api/fiscal_service");
        MockRestServiceServer localServer = MockRestServiceServer.bindTo(builder).build();
        ExchangeRateClient localClient = new ExchangeRateClient(builder.build(), "v1/accounting/od/rates_of_exchange", 3);

        localServer.expect(requestTo(org.hamcrest.Matchers.startsWith(
                        "https://api.fiscaldata.treasury.gov/services/api/fiscal_service/v1/accounting/od/rates_of_exchange")))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {
                          "data": []
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThat(localClient.findExchangeRate("Canada-Dollar", LocalDate.of(2026, 3, 15))).isEmpty();
    }

    @Test
    void rejectsBlankConfiguredPath() {
        RestClient restClient = RestClient.builder().build();

        assertThatThrownBy(() -> new ExchangeRateClient(restClient, " ", 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("treasury.api.rates-of-exchange-path must not be blank");
    }

    @Test
    void rejectsInvalidMaxAttempts() {
        RestClient restClient = RestClient.builder().build();

        assertThatThrownBy(() -> new ExchangeRateClient(restClient, "/v1/accounting/od/rates_of_exchange", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("treasury.api.max-attempts must be greater than 0");
    }

    @Test
    void retriesUpstreamErrorsAndSucceedsOnThirdAttempt() {
        server.expect(ExpectedCount.times(2), requestTo(org.hamcrest.Matchers.startsWith(
                        "https://api.fiscaldata.treasury.gov/services/api/fiscal_service/v1/accounting/od/rates_of_exchange")))
                .andExpect(method(GET))
                .andRespond(withStatus(INTERNAL_SERVER_ERROR));

        server.expect(requestTo(org.hamcrest.Matchers.startsWith(
                        "https://api.fiscaldata.treasury.gov/services/api/fiscal_service/v1/accounting/od/rates_of_exchange")))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {
                          "data": [
                            {
                              "country_currency_desc": "Canada-Dollar",
                              "exchange_rate": "1.4000",
                              "record_date": "2025-12-31"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        ExchangeRateQuote quote = client.findExchangeRate("Canada-Dollar", LocalDate.of(2026, 3, 15)).orElseThrow();

        assertThat(quote.exchangeRateDate()).isEqualTo(LocalDate.of(2025, 12, 31));
        assertThat(quote.exchangeRate().toPlainString()).isEqualTo("1.4000");
    }

    @Test
    void doesNotRetryNonRetryableClientErrors() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(
                        "https://api.fiscaldata.treasury.gov/services/api/fiscal_service/v1/accounting/od/rates_of_exchange")))
                .andExpect(method(GET))
                .andRespond(withStatus(NOT_FOUND));

        assertThatThrownBy(() -> client.findExchangeRate("Canada-Dollar", LocalDate.of(2026, 3, 15)))
                .isInstanceOf(UpstreamExchangeRateException.class)
                .hasMessage("Treasury exchange-rate API request failed");
    }
}
