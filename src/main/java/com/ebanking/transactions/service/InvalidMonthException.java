package com.ebanking.transactions.service;

public class InvalidMonthException extends RuntimeException {

    public InvalidMonthException(String month) {
        super("month must use yyyy-MM format: " + month);
    }
}
