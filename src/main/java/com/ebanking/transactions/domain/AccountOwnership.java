package com.ebanking.transactions.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "account_ownership")
public class AccountOwnership {

    @Id
    @Column(name = "iban", nullable = false, length = 64)
    private String iban;

    @Column(name = "customer_id", nullable = false, length = 64)
    private String customerId;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    protected AccountOwnership() {
    }

    public AccountOwnership(String iban, String customerId, String currency) {
        this.iban = iban;
        this.customerId = customerId;
        this.currency = currency;
    }

    public String getIban() {
        return iban;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getCurrency() {
        return currency;
    }
}
