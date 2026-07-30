package com.ebanking.transactions.exchange;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Component
@EnableConfigurationProperties(ExchangeRateProperties.class)
public class ExternalExchangeRateClient implements ExchangeRateClient {

    private static final Logger log = LoggerFactory.getLogger(ExternalExchangeRateClient.class);

    private final RestClient restClient;
    private final ExchangeRateProperties properties;
    private final Clock clock;
    private final Map<RateKey, CachedRate> cache = new ConcurrentHashMap<>();

    public ExternalExchangeRateClient(RestClient.Builder builder,
                                      ExchangeRateProperties properties,
                                      Clock clock) {
        this.properties = properties;
        this.clock = clock;
        String baseUrl = Objects.requireNonNullElse(properties.baseUrl(), "http://localhost:9099");
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    @Override
    public BigDecimal currentRate(String sourceCurrency, String targetCurrency) {
        String source = normalize(sourceCurrency);
        String target = normalize(targetCurrency);
        if (source.equals(target)) {
            return BigDecimal.ONE;
        }

        RateKey key = new RateKey(source, target);
        CachedRate cached = cache.get(key);
        Instant now = clock.instant();
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.rate();
        }

        BigDecimal rate = fetchRateWithSingleRetry(source, target);
        cache.put(key, new CachedRate(rate, now.plus(properties.cacheTtl())));
        return rate;
    }

    private BigDecimal fetchRateWithSingleRetry(String source, String target) {
        RuntimeException firstFailure = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                ExchangeRateResponse response = restClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/rates")
                                .queryParam("base", source)
                                .queryParam("symbols", target)
                                .build())
                        .retrieve()
                        .body(ExchangeRateResponse.class);
                if (response == null || response.rates() == null || !response.rates().containsKey(target)) {
                    throw new IllegalStateException("Exchange-rate provider did not return " + target);
                }
                return response.rates().get(target);
            } catch (RuntimeException exception) {
                firstFailure = exception;
                log.warn("Exchange-rate lookup failed for {}/{} on attempt {}", source, target, attempt);
            }
        }
        throw firstFailure;
    }

    private String normalize(String currency) {
        if (currency == null || !currency.matches("[A-Za-z]{3}")) {
            throw new IllegalArgumentException("Currency must be an ISO-4217 code");
        }
        return currency.toUpperCase(Locale.ROOT);
    }

    private record RateKey(String source, String target) {
    }

    private record CachedRate(BigDecimal rate, Instant expiresAt) {
    }
}
