package com.ebanking.transactions.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountOwnershipRepository extends JpaRepository<AccountOwnership, String> {
}
