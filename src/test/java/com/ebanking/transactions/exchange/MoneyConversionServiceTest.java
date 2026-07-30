package com.ebanking.transactions.exchange;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyConversionServiceTest {

    @Test
    void convertsAndRoundsToMinorUnits() {
        MoneyConversionService service = new MoneyConversionService((source, target) -> new BigDecimal("1.2345"));

        BigDecimal converted = service.convert(new BigDecimal("10.00"), "GBP", "CHF");

        assertThat(converted).isEqualByComparingTo("12.35");
    }
}
