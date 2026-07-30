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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
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

        saveTransaction(
                "credit-1",
                CUSTOMER_ID,
                new BigDecimal("100.00"),
                "CHF",
                "CH93-0000-0000-0000-0000-0",
                LocalDate.of(2020, 10, 2),
                "Salary"
        );
        saveTransaction(
                "debit-1",
                CUSTOMER_ID,
                new BigDecimal("-50.00"),
                "GBP",
                "GB11-0000-0000-0000-0000-0",
                LocalDate.of(2020, 10, 1),
                "Online payment"
        );
        saveTransaction(
                "other-customer",
                "P-9999999999",
                new BigDecimal("999.00"),
                "EUR",
                "DE22-0000-0000-0000-0000-0",
                LocalDate.of(2020, 10, 1),
                "Hidden from caller"
        );
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
    void paginatesDeterministicallyAndCalculatesTotalsForCurrentPageOnly() throws Exception {
        saveTransaction(
                "credit-2",
                CUSTOMER_ID,
                new BigDecimal("40.00"),
                "CHF",
                "CH93-0000-0000-0000-0000-0",
                LocalDate.of(2020, 10, 3),
                "Refund"
        );
        saveTransaction(
                "debit-2",
                CUSTOMER_ID,
                new BigDecimal("-10.00"),
                "CHF",
                "CH93-0000-0000-0000-0000-0",
                LocalDate.of(2020, 10, 3),
                "Fee"
        );

        mockMvc.perform(get("/api/v1/transactions")
                        .param("month", "2020-10")
                        .param("targetCurrency", "CHF")
                        .param("page", "0")
                        .param("size", "2")
                        .with(jwt().jwt(token -> token.claim("customer_id", CUSTOMER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions", hasSize(2)))
                .andExpect(jsonPath("$.transactions[0].id").value("credit-2"))
                .andExpect(jsonPath("$.transactions[1].id").value("debit-2"))
                .andExpect(jsonPath("$.totalCredit.amount").value(40.00))
                .andExpect(jsonPath("$.totalDebit.amount").value(10.00))
                .andExpect(jsonPath("$.page.page").value(0))
                .andExpect(jsonPath("$.page.size").value(2))
                .andExpect(jsonPath("$.page.totalElements").value(4))
                .andExpect(jsonPath("$.page.totalPages").value(2));
    }

    @Test
    void filtersTransactionsToRequestedCalendarMonth() throws Exception {
        saveTransaction(
                "november-1",
                CUSTOMER_ID,
                new BigDecimal("500.00"),
                "CHF",
                "CH93-0000-0000-0000-0000-0",
                LocalDate.of(2020, 11, 1),
                "Next month"
        );

        mockMvc.perform(get("/api/v1/transactions")
                        .param("month", "2020-11")
                        .param("targetCurrency", "CHF")
                        .with(jwt().jwt(token -> token.claim("customer_id", CUSTOMER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions", hasSize(1)))
                .andExpect(jsonPath("$.transactions[0].id").value("november-1"))
                .andExpect(jsonPath("$.totalCredit.amount").value(500.00))
                .andExpect(jsonPath("$.totalDebit.amount").value(0));
    }

    @Test
    void rejectsInvalidQueryParameters() throws Exception {
        mockMvc.perform(get("/api/v1/transactions")
                        .param("month", "2020-13")
                        .with(jwt().jwt(token -> token.claim("customer_id", CUSTOMER_ID))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/transactions")
                        .param("month", "2020-10")
                        .param("targetCurrency", "chf")
                        .with(jwt().jwt(token -> token.claim("customer_id", CUSTOMER_ID))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/transactions")
                        .param("month", "2020-10")
                        .param("size", "201")
                        .with(jwt().jwt(token -> token.claim("customer_id", CUSTOMER_ID))))
                .andExpect(status().isBadRequest());
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

    @Test
    void servesDemoUiWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("index.html"));

        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Monthly account activity")));

        mockMvc.perform(get("/css/app.css"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/js/app.js"))
                .andExpect(status().isOk());
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

    private MoneyAccountTransaction saveTransaction(String transactionId,
                                                    String customerId,
                                                    BigDecimal amount,
                                                    String currency,
                                                    String accountIban,
                                                    LocalDate valueDate,
                                                    String description) {
        return transactionRepository.save(new MoneyAccountTransaction(
                transactionId,
                customerId,
                amount,
                currency,
                accountIban,
                valueDate,
                description,
                Instant.parse("2026-01-01T00:00:00Z")
        ));
    }
}
