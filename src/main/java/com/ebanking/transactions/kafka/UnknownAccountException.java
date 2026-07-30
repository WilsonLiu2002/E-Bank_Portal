package com.ebanking.transactions.kafka;

public class UnknownAccountException extends RuntimeException {

    public UnknownAccountException(String iban) {
        super("No account ownership is registered for IBAN " + iban);
    }
}
