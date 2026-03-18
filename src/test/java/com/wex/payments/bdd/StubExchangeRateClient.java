package com.wex.payments.bdd;

import com.wex.payments.service.ExchangeRateClient;
import com.wex.payments.service.ExchangeRateQuote;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

class StubExchangeRateClient extends ExchangeRateClient {

    private final List<ExchangeRateQuote> quotes = new ArrayList<>();

    StubExchangeRateClient() {
        super(null, "/unused");
    }

    void reset() {
        quotes.clear();
    }

    void setQuotes(List<ExchangeRateQuote> configuredQuotes) {
        quotes.clear();
        quotes.addAll(configuredQuotes);
    }

    @Override
    public Optional<ExchangeRateQuote> findExchangeRate(String countryCurrency, LocalDate purchaseDate) {
        LocalDate lowerBound = purchaseDate.minusMonths(6);

        return quotes.stream()
                .filter(quote -> quote.countryCurrency().equals(countryCurrency))
                .filter(quote -> !quote.exchangeRateDate().isAfter(purchaseDate))
                .filter(quote -> !quote.exchangeRateDate().isBefore(lowerBound))
                .max(Comparator.comparing(ExchangeRateQuote::exchangeRateDate));
    }
}
