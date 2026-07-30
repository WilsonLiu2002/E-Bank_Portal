package com.ebanking.transactions.api;

import com.ebanking.transactions.domain.AccountOwnership;
import com.ebanking.transactions.domain.AccountOwnershipRepository;
import com.ebanking.transactions.domain.MoneyAccountTransaction;
import com.ebanking.transactions.domain.MoneyAccountTransactionRepository;
import com.ebanking.transactions.exchange.ExchangeRateClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TransactionControllerIT {

    private static final String CUSTOMER_ID = "P-0123456789";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountOwnershipRepository accountOwnershipRepository;

    @Autowired
    private MoneyAccountTransactionRepository transactionRepository;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        accountOwnershipRepository.deleteAll();

        accountOwnershipRepository.save(new AccountOwnership("CH93-0000-0000-0000-0000-0", CUSTOMER_ID, "CHF"));
        accountOwnershipRepository.save(new AccountOwnership("GB11-0000-0000-0000-0000-0", CUSTOMER_ID, "GBP"));
        accountOwnershipRepository.save(new AccountOwnership("DE22-0000-0000-0000-0000-0", "P-9999999999", "EUR"));

        transactionRepository.save(new MoneyAccountTransaction(
                "credit-1",
                CUSTOMER_ID,
                new BigDecimal("100.00"),
                "CHF",
                "CH93-0000-0000-0000-0000-0",
                LocalDate.of(2020, 10, 2),
                "Salary",
                Instant.parse("2026-01-01T00:00:00Z")
        ));
        transactionRepository.save(new MoneyAccountTransaction(
                "debit-1",
                CUSTOMER_ID,
                new BigDecimal("-50.00"),
                "GBP",
                "GB11-0000-0000-0000-0000-0",
                LocalDate.of(2020, 10, 1),
                "Online payment",
                Instant.parse("2026-01-01T00:00:00Z")
        ));
        transactionRepository.save(new MoneyAccountTransaction(
                "other-customer",
                "P-9999999999",
                new BigDecimal("999.00"),
                "EUR",
                "DE22-0000-0000-0000-0000-0",
                LocalDate.of(2020, 10, 1),
                "Hidden from caller",
                Instant.parse("2026-01-01T00:00:00Z")
        ));
    }

    @Test
    void returnsOnlyAuthenticatedCustomersTransactionsWithConvertedPageTotals() throws Exception {
        mockMvc.perform(get("/api/v1/transactions")
                        .param("month", "2020-10")
                        .param("targetCurrency", "CHF")
                        .with(jwt().jwt(token -> token.claim("customer_id", CUSTOMER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions", hasSize(2)))
                .andExpect(jsonPath("$.transactions[0].id").value("credit-1"))
                .andExpect(jsonPath("$.transactions[1].id").value("debit-1"))
                .andExpect(jsonPath("$.totalCredit.amount").value(100.00))
                .andExpect(jsonPath("$.totalCredit.currency").value("CHF"))
                .andExpect(jsonPath("$.totalDebit.amount").value(60.00))
                .andExpect(jsonPath("$.totalDebit.currency").value("CHF"))
                .andExpect(jsonPath("$.page.totalElements").value(2));
    }

    @Test
    void rejectsUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/v1/transactions").param("month", "2020-10"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void exposesOpenApiContractWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/transactions']").exists());
    }

    @TestConfiguration
    static class ExchangeRateTestConfig {

        @Bean
        @Primary
        ExchangeRateClient exchangeRateClient() {
            return (sourceCurrency, targetCurrency) -> {
                if ("GBP".equals(sourceCurrency) && "CHF".equals(targetCurrency)) {
                    return new BigDecimal("1.20");
                }
                return BigDecimal.ONE;
            };
        }
    }
}
