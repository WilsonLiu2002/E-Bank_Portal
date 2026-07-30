package com.ebanking.transactions.kafka;

import com.ebanking.transactions.domain.AccountOwnership;
import com.ebanking.transactions.domain.AccountOwnershipRepository;
import com.ebanking.transactions.domain.MoneyAccountTransaction;
import com.ebanking.transactions.domain.MoneyAccountTransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class TransactionIngestionServiceTest {

    @Autowired
    private TransactionIngestionService ingestionService;

    @Autowired
    private AccountOwnershipRepository accountOwnershipRepository;

    @Autowired
    private MoneyAccountTransactionRepository transactionRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        accountOwnershipRepository.deleteAll();
        accountOwnershipRepository.save(new AccountOwnership("CH93-0000-0000-0000-0000-0", "P-0123456789", "CHF"));
    }

    @Test
    void ingestsKafkaMessageIntoCustomerReadModel() {
        ingestionService.ingest("tx-1", new TransactionMessage(
                1,
                new BigDecimal("-75.00"),
                "chf",
                "CH93-0000-0000-0000-0000-0",
                LocalDate.of(2020, 10, 1),
                "Card payment"
        ));

        MoneyAccountTransaction transaction = transactionRepository.findById("tx-1").orElseThrow();
        assertThat(transaction.getCustomerId()).isEqualTo("P-0123456789");
        assertThat(transaction.getCurrency()).isEqualTo("CHF");
        assertThat(transaction.getAmount()).isEqualByComparingTo("-75.00");
    }

    @Test
    void upsertsExistingTransactionByKafkaKey() {
        ingestionService.ingest("tx-1", new TransactionMessage(
                1,
                new BigDecimal("-75.00"),
                "CHF",
                "CH93-0000-0000-0000-0000-0",
                LocalDate.of(2020, 10, 1),
                "Initial"
        ));

        ingestionService.ingest("tx-1", new TransactionMessage(
                1,
                new BigDecimal("-80.00"),
                "CHF",
                "CH93-0000-0000-0000-0000-0",
                LocalDate.of(2020, 10, 2),
                "Corrected"
        ));

        MoneyAccountTransaction transaction = transactionRepository.findById("tx-1").orElseThrow();
        assertThat(transaction.getAmount()).isEqualByComparingTo("-80.00");
        assertThat(transaction.getValueDate()).isEqualTo(LocalDate.of(2020, 10, 2));
        assertThat(transaction.getDescription()).isEqualTo("Corrected");
        assertThat(transactionRepository.count()).isEqualTo(1);
    }

    @Test
    void failsFastForUnknownAccountOwnership() {
        assertThatThrownBy(() -> ingestionService.ingest("tx-2", new TransactionMessage(
                1,
                new BigDecimal("10.00"),
                "CHF",
                "UNKNOWN-IBAN",
                LocalDate.of(2020, 10, 1),
                "Unknown"
        ))).isInstanceOf(UnknownAccountException.class);
    }

    @Test
    void rejectsMissingKafkaTransactionKey() {
        TransactionMessage message = new TransactionMessage(
                1,
                new BigDecimal("10.00"),
                "CHF",
                "CH93-0000-0000-0000-0000-0",
                LocalDate.of(2020, 10, 1),
                "Missing key"
        );

        assertThatThrownBy(() -> ingestionService.ingest(" ", message))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Kafka key");
    }

    @Test
    void ignoresUnknownJsonFieldsForAdditiveSchemaEvolution() throws Exception {
        TransactionMessage message = objectMapper.readValue("""
                {
                  "schemaVersion": 1,
                  "amount": -12.50,
                  "currency": "CHF",
                  "accountIban": "CH93-0000-0000-0000-0000-0",
                  "valueDate": "2020-10-04",
                  "description": "Card payment",
                  "merchantCategory": "retail"
                }
                """, TransactionMessage.class);

        ingestionService.ingest("tx-additive-schema", message);

        MoneyAccountTransaction transaction = transactionRepository.findById("tx-additive-schema").orElseThrow();
        assertThat(transaction.getDescription()).isEqualTo("Card payment");
        assertThat(transaction.getAmount()).isEqualByComparingTo("-12.50");
    }
}
