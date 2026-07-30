package com.ebanking.transactions.api;

import java.time.Instant;

public record ApiErrorDto(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path
) {
}
