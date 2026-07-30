package com.ebanking.transactions.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface MoneyAccountTransactionRepository extends JpaRepository<MoneyAccountTransaction, String> {

    Page<MoneyAccountTransaction> findByCustomerIdAndValueDateGreaterThanEqualAndValueDateLessThan(
            String customerId,
            LocalDate fromInclusive,
            LocalDate toExclusive,
            Pageable pageable
    );
}
