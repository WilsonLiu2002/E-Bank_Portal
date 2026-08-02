package com.ebanking.transactions.exchange;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;

class ExternalExchangeRateClientTest {

    private static final Instant NOW = Instant.parse("2026-08-02T12:00:00Z");
    private static final ExchangeRateProperties PROPERTIES =
            new ExchangeRateProperties("http://rates.test", Duration.ofMinutes(5));

    @Test
    void returnsOneWithoutCallingProviderWhenCurrenciesMatch() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ExternalExchangeRateClient client = client(builder);

        BigDecimal rate = client.currentRate("chf", "CHF");

        assertThat(rate).isEqualByComparingTo(BigDecimal.ONE);
        server.verify();
    }

    @Test
    void fetchesAndNormalizesCurrencyPair() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ExternalExchangeRateClient client = client(builder);
        server.expect(once(), requestTo("http://rates.test/rates?base=GBP&symbols=CHF"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {
                          "base": "GBP",
                          "rates": {
                            "CHF": 1.11
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        BigDecimal rate = client.currentRate("gbp", "chf");

        assertThat(rate).isEqualByComparingTo("1.11");
        server.verify();
    }

    @Test
    void cachesRateUntilTtlExpires() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ExternalExchangeRateClient client = client(builder);
        server.expect(once(), requestTo("http://rates.test/rates?base=EUR&symbols=CHF"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {
                          "base": "EUR",
                          "rates": {
                            "CHF": 0.97
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        BigDecimal first = client.currentRate("EUR", "CHF");
        BigDecimal second = client.currentRate("EUR", "CHF");

        assertThat(first).isEqualByComparingTo("0.97");
        assertThat(second).isEqualByComparingTo("0.97");
        server.verify();
    }

    @Test
    void retriesOnceAfterProviderFailure() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ExternalExchangeRateClient client = client(builder);
        server.expect(once(), requestTo("http://rates.test/rates?base=GBP&symbols=CHF"))
                .andExpect(method(GET))
                .andRespond(withServerError());
        server.expect(once(), requestTo("http://rates.test/rates?base=GBP&symbols=CHF"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {
                          "base": "GBP",
                          "rates": {
                            "CHF": 1.13
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        BigDecimal rate = client.currentRate("GBP", "CHF");

        assertThat(rate).isEqualByComparingTo("1.13");
        server.verify();
    }

    @Test
    void failsWhenProviderDoesNotReturnRequestedCurrency() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ExternalExchangeRateClient client = client(builder);
        server.expect(once(), requestTo("http://rates.test/rates?base=GBP&symbols=CHF"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {
                          "base": "GBP",
                          "rates": {
                            "EUR": 1.13
                          }
                        }
                        """, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("http://rates.test/rates?base=GBP&symbols=CHF"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {
                          "base": "GBP",
                          "rates": {
                            "EUR": 1.13
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.currentRate("GBP", "CHF"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Exchange-rate provider did not return CHF");
        server.verify();
    }

    @Test
    void rejectsInvalidCurrency() {
        RestClient.Builder builder = RestClient.builder();
        ExternalExchangeRateClient client = client(builder);

        assertThatThrownBy(() -> client.currentRate("GB", "CHF"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Currency must be an ISO-4217 code");
    }

    private ExternalExchangeRateClient client(RestClient.Builder builder) {
        return new ExternalExchangeRateClient(builder, PROPERTIES, Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
