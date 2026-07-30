package com.ebanking.transactions.security;

public class MissingCustomerIdentityException extends RuntimeException {

    public MissingCustomerIdentityException(String message) {
        super(message);
    }
}
