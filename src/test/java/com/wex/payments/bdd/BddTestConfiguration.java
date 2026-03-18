package com.wex.payments.bdd;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
class BddTestConfiguration {

    @Bean
    @Primary
    StubExchangeRateClient stubExchangeRateClient() {
        return new StubExchangeRateClient();
    }
}
