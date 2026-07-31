package com.ebanking.transactions.kafka;

import com.ebanking.transactions.domain.AccountOwnership;
import com.ebanking.transactions.domain.AccountOwnershipRepository;
import com.ebanking.transactions.domain.MoneyAccountTransaction;
import com.ebanking.transactions.domain.MoneyAccountTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

@Service
public class TransactionIngestionService {

    private final AccountOwnershipRepository accountOwnershipRepository;
    private final MoneyAccountTransactionRepository transactionRepository;
    private final Clock clock;

    public TransactionIngestionService(AccountOwnershipRepository accountOwnershipRepository,
                                       MoneyAccountTransactionRepository transactionRepository,
                                       Clock clock) {
        this.accountOwnershipRepository = accountOwnershipRepository;
        this.transactionRepository = transactionRepository;
        this.clock = clock;
    }

    @Transactional
    public MoneyAccountTransaction ingest(String transactionId, TransactionMessage message) {
        message.validate(transactionId);
        AccountOwnership owner = accountOwnershipRepository.findById(message.accountIban())
                .orElseThrow(() -> new UnknownAccountException(message.accountIban()));
        Instant now = clock.instant();
        return transactionRepository.findById(transactionId).map(existing -> {
            existing.replaceWith(
                    message.amount(),
                    message.currency().toUpperCase(Locale.ROOT),
                    message.accountIban(),
                    message.valueDate(),
                    message.description(),
                    now
            );
            return existing;
        }).orElseGet(() -> transactionRepository.save(new MoneyAccountTransaction(
                transactionId,
                owner.getCustomerId(),
                message.amount(),
                message.currency().toUpperCase(Locale.ROOT),
                message.accountIban(),
                message.valueDate(),
                message.description(),
                now
        )));
    }
}
