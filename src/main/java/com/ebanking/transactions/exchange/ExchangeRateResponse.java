package com.ebanking.transactions.exchange;

import java.math.BigDecimal;
import java.util.Map;

record ExchangeRateResponse(String base, Map<String, BigDecimal> rates) {
}
