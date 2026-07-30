package com.ebanking.transactions.exchange;

import java.math.BigDecimal;

public interface ExchangeRateClient {

    BigDecimal currentRate(String sourceCurrency, String targetCurrency);
}
