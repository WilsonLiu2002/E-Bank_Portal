package com.ebanking.transactions.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

public record MoneyDto(
        @Schema(example = "125.40")
        BigDecimal amount,
        @Schema(example = "CHF")
        String currency
) {
}
