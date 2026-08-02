package com.ebanking.transactions.api;

import java.util.List;

public record TransactionPageDto(
        List<TransactionDto> transactions,
        MoneyDto totalCredit,
        MoneyDto totalDebit,
        PageMetadataDto page
) {
    public TransactionPageDto {
        transactions = List.copyOf(transactions);
    }
}
