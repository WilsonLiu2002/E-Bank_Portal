package com.ebanking.transactions.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "money_account_transaction")
public class MoneyAccountTransaction {

    @Id
    @Column(name = "transaction_id", nullable = false, length = 80)
    private String transactionId;

    @Column(name = "customer_id", nullable = false, length = 64)
    private String customerId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "account_iban", nullable = false, length = 64)
    private String accountIban;

    @Column(name = "value_date", nullable = false)
    private LocalDate valueDate;

    @Column(name = "description", nullable = false, length = 512)
    private String description;

    @Column(name = "ingested_at", nullable = false)
    private Instant ingestedAt;

    @Version
    @Column(name = "record_version", nullable = false)
    private long recordVersion;

    protected MoneyAccountTransaction() {
    }

    public MoneyAccountTransaction(String transactionId,
                                   String customerId,
                                   BigDecimal amount,
                                   String currency,
                                   String accountIban,
                                   LocalDate valueDate,
                                   String description,
                                   Instant ingestedAt) {
        this.transactionId = transactionId;
        this.customerId = customerId;
        this.amount = amount;
        this.currency = currency;
        this.accountIban = accountIban;
        this.valueDate = valueDate;
        this.description = description;
        this.ingestedAt = ingestedAt;
    }

    public void replaceWith(BigDecimal amount,
                            String currency,
                            String accountIban,
                            LocalDate valueDate,
                            String description,
                            Instant ingestedAt) {
        this.amount = amount;
        this.currency = currency;
        this.accountIban = accountIban;
        this.valueDate = valueDate;
        this.description = description;
        this.ingestedAt = ingestedAt;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getAccountIban() {
        return accountIban;
    }

    public LocalDate getValueDate() {
        return valueDate;
    }

    public String getDescription() {
        return description;
    }
}
