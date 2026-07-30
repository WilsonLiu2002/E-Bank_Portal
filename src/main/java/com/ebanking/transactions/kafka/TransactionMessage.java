package com.ebanking.transactions.kafka;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionMessage(
        Integer schemaVersion,
        BigDecimal amount,
        String currency,
        String accountIban,
        LocalDate valueDate,
        String description
) {
    void validate(String transactionId) {
        if (transactionId == null || transactionId.isBlank()) {
            throw new IllegalArgumentException("Kafka key must contain the transaction ID");
        }
        if (amount == null) {
            throw new IllegalArgumentException("amount is required");
        }
        if (currency == null || !currency.matches("[A-Za-z]{3}")) {
            throw new IllegalArgumentException("currency must be an ISO-4217 code");
        }
        if (accountIban == null || accountIban.isBlank()) {
            throw new IllegalArgumentException("accountIban is required");
        }
        if (valueDate == null) {
            throw new IllegalArgumentException("valueDate is required");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description is required");
        }
    }
}
