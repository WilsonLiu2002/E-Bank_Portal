package com.ebanking.transactions.infra;

import com.ebanking.transactions.domain.AccountOwnership;
import com.ebanking.transactions.domain.AccountOwnershipRepository;
import com.ebanking.transactions.domain.MoneyAccountTransactionRepository;
import com.ebanking.transactions.exchange.ExchangeRateClient;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=true",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.consumer.group-id=transaction-service-container-it",
        "transactions.kafka.topic=money-account-transactions-container-it"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TransactionServiceContainerIT {

    private static final String TOPIC = "money-account-transactions-container-it";
    private static final String CUSTOMER_ID = "P-0123456789";
    private static final String OTHER_CUSTOMER_ID = "P-9999999999";

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("transactions")
            .withUsername("transactions")
            .withPassword("transactions");

    @Container
    static final KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.1")
    );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountOwnershipRepository accountOwnershipRepository;

    @Autowired
    private MoneyAccountTransactionRepository transactionRepository;

    @DynamicPropertySource
    static void registerContainerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        accountOwnershipRepository.deleteAll();
        accountOwnershipRepository.save(new AccountOwnership("CH93-CONTAINER-CHF-0", CUSTOMER_ID, "CHF"));
        accountOwnershipRepository.save(new AccountOwnership("CH93-CONTAINER-GBP-1", CUSTOMER_ID, "GBP"));
        accountOwnershipRepository.save(new AccountOwnership("CH93-CONTAINER-EUR-2", OTHER_CUSTOMER_ID, "EUR"));
    }

    @Test
    void consumesKafkaRecordsIntoPostgresReadModelAndServesSecuredApiPage() throws Exception {
        publish("tc-credit-1", """
                {
                  "schemaVersion": 1,
                  "amount": 125.00,
                  "currency": "CHF",
                  "accountIban": "CH93-CONTAINER-CHF-0",
                  "valueDate": "2021-01-06",
                  "description": "Container salary"
                }
                """);
        publish("tc-debit-1", """
                {
                  "schemaVersion": 1,
                  "amount": -20.00,
                  "currency": "GBP",
                  "accountIban": "CH93-CONTAINER-GBP-1",
                  "valueDate": "2021-01-05",
                  "description": "Container card payment"
                }
                """);
        publish("tc-other-customer", """
                {
                  "schemaVersion": 1,
                  "amount": 999.00,
                  "currency": "EUR",
                  "accountIban": "CH93-CONTAINER-EUR-2",
                  "valueDate": "2021-01-04",
                  "description": "Hidden from caller"
                }
                """);

        awaitTransactions("tc-credit-1", "tc-debit-1", "tc-other-customer");

        mockMvc.perform(get("/api/v1/transactions")
                        .param("month", "2021-01")
                        .param("targetCurrency", "CHF")
                        .param("page", "0")
                        .param("size", "20")
                        .with(jwt().jwt(token -> token.claim("customer_id", CUSTOMER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions.length()").value(2))
                .andExpect(jsonPath("$.transactions[0].id").value("tc-credit-1"))
                .andExpect(jsonPath("$.transactions[1].id").value("tc-debit-1"))
                .andExpect(jsonPath("$.totalCredit.amount").value(125.00))
                .andExpect(jsonPath("$.totalCredit.currency").value("CHF"))
                .andExpect(jsonPath("$.totalDebit.amount").value(30.00))
                .andExpect(jsonPath("$.totalDebit.currency").value("CHF"))
                .andExpect(jsonPath("$.page.totalElements").value(2));
    }

    private void publish(String key, String payload) throws Exception {
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProperties())) {
            producer.send(new ProducerRecord<>(TOPIC, key, payload)).get(10, TimeUnit.SECONDS);
            producer.flush();
        }
    }

    private Properties producerProperties() {
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.ACKS_CONFIG, "all");
        return properties;
    }

    private void awaitTransactions(String... transactionIds) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
        while (Instant.now().isBefore(deadline)) {
            if (Arrays.stream(transactionIds).allMatch(transactionId -> transactionRepository.existsById(transactionId))) {
                return;
            }
            Thread.sleep(200);
        }
        assertThat(transactionRepository.findAll())
                .extracting(transaction -> transaction.getTransactionId())
                .contains(transactionIds);
    }

    @TestConfiguration
    static class ExchangeRateTestConfig {

        @Bean
        @Primary
        ExchangeRateClient exchangeRateClient() {
            Map<String, BigDecimal> rates = Map.of(
                    "GBP/CHF", new BigDecimal("1.50")
            );
            return (sourceCurrency, targetCurrency) ->
                    rates.getOrDefault(sourceCurrency + "/" + targetCurrency, BigDecimal.ONE);
        }
    }
}
