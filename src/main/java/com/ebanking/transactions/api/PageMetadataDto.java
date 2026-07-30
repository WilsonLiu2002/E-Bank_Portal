package com.ebanking.transactions.api;

public record PageMetadataDto(
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
