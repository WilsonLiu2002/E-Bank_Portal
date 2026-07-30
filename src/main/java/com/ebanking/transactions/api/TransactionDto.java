package com.ebanking.transactions.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

public record TransactionDto(
        @Schema(example = "89d3o179-abcd-465b-o9ee-e2d5f6ofEld46")
        String id,
        MoneyDto amount,
        @Schema(example = "CH93-0000-0000-0000-0000-0")
        String accountIban,
        @Schema(example = "2020-10-01")
        LocalDate valueDate,
        @Schema(example = "Online payment CHF")
        String description
) {
}
