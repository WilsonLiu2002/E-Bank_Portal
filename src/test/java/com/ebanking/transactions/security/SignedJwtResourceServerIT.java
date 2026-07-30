package com.ebanking.transactions.security;

import com.ebanking.transactions.domain.AccountOwnership;
import com.ebanking.transactions.domain.AccountOwnershipRepository;
import com.ebanking.transactions.domain.MoneyAccountTransaction;
import com.ebanking.transactions.domain.MoneyAccountTransactionRepository;
import com.ebanking.transactions.exchange.ExchangeRateClient;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.LocalDate;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SignedJwtResourceServerIT {

    private static final String CUSTOMER_ID = "P-0123456789";
    private static final KeyPair KEY_PAIR = generateKeyPair();
    private static final RSAPublicKey publicKey = (RSAPublicKey) KEY_PAIR.getPublic();
    private static final RSAPrivateKey privateKey = (RSAPrivateKey) KEY_PAIR.getPrivate();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private AccountOwnershipRepository accountOwnershipRepository;

    @Autowired
    private MoneyAccountTransactionRepository transactionRepository;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        accountOwnershipRepository.deleteAll();
        accountOwnershipRepository.save(new AccountOwnership("CH93-0000-0000-0000-0000-0", CUSTOMER_ID, "CHF"));
        transactionRepository.save(new MoneyAccountTransaction(
                "signed-jwt-visible",
                CUSTOMER_ID,
                new BigDecimal("42.00"),
                "CHF",
                "CH93-0000-0000-0000-0000-0",
                LocalDate.of(2021, 1, 4),
                "Visible through signed JWT",
                Instant.parse("2026-01-01T00:00:00Z")
        ));
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to generate test RSA key pair", exception);
        }
    }

    @Test
    void acceptsRealSignedJwtBearerTokenAndScopesRowsToCustomerClaim() throws Exception {
        String token = signedToken(CUSTOMER_ID);

        mockMvc.perform(get("/api/v1/transactions")
                        .header("Authorization", "Bearer " + token)
                        .param("month", "2021-01")
                        .param("targetCurrency", "CHF"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions", hasSize(1)))
                .andExpect(jsonPath("$.transactions[0].id").value("signed-jwt-visible"))
                .andExpect(jsonPath("$.totalCredit.amount").value(42.00));
    }

    @Test
    void rejectsTamperedSignedJwtBearerToken() throws Exception {
        String token = signedToken(CUSTOMER_ID);
        String tamperedToken = token.substring(0, token.length() - 2) + "xx";

        mockMvc.perform(get("/api/v1/transactions")
                        .header("Authorization", "Bearer " + tamperedToken)
                        .param("month", "2021-01"))
                .andExpect(status().isUnauthorized());
    }

    private String signedToken(String customerId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("local-test")
                .subject(customerId)
                .claim("customer_id", customerId)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(600))
                .build();
        JwsHeader headers = JwsHeader.with(SignatureAlgorithm.RS256)
                .keyId("test-key")
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(headers, claims)).getTokenValue();
    }

    @TestConfiguration
    static class SignedJwtTestConfig {

        @Bean
        @Primary
        JwtDecoder jwtDecoder() {
            return NimbusJwtDecoder.withPublicKey(publicKey).build();
        }

        @Bean
        JwtEncoder jwtEncoder() {
            RSAKey rsaKey = new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .keyID("test-key")
                    .build();
            JWKSource<SecurityContext> jwkSource = (jwkSelector, securityContext) ->
                    jwkSelector.select(new JWKSet(rsaKey));
            return new NimbusJwtEncoder(jwkSource);
        }

        @Bean
        @Primary
        ExchangeRateClient exchangeRateClient() {
            return (sourceCurrency, targetCurrency) -> BigDecimal.ONE;
        }
    }
}
