package com.ebanking.transactions.exchange;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class MoneyConversionService {

    private final ExchangeRateClient exchangeRateClient;

    public MoneyConversionService(ExchangeRateClient exchangeRateClient) {
        this.exchangeRateClient = exchangeRateClient;
    }

    public BigDecimal convert(BigDecimal amount, String sourceCurrency, String targetCurrency) {
        BigDecimal rate = exchangeRateClient.currentRate(sourceCurrency, targetCurrency);
        return amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }
}
