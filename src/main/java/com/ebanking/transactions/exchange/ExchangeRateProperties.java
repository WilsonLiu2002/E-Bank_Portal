package com.ebanking.transactions.exchange;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "exchange-rate")
public record ExchangeRateProperties(
        String baseUrl,
        Duration cacheTtl
) {
    public Duration cacheTtl() {
        return cacheTtl == null ? Duration.ofMinutes(5) : cacheTtl;
    }
}
