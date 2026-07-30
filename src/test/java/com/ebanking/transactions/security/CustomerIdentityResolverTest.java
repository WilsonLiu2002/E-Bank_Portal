package com.ebanking.transactions.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerIdentityResolverTest {

    private final CustomerIdentityResolver resolver = new CustomerIdentityResolver();

    @Test
    void resolvesPreferredCustomerClaim() {
        Jwt jwt = jwt(Map.of("customer_id", "P-0123456789", "sub", "fallback"));

        assertThat(resolver.resolve(jwt)).isEqualTo("P-0123456789");
    }

    @Test
    void fallsBackToSubjectClaim() {
        Jwt jwt = jwt(Map.of("sub", "P-0123456789"));

        assertThat(resolver.resolve(jwt)).isEqualTo("P-0123456789");
    }

    @Test
    void rejectsTokenWithoutCustomerIdentity() {
        Jwt jwt = jwt(Map.of("aud", "portal"));

        assertThatThrownBy(() -> resolver.resolve(jwt))
                .isInstanceOf(MissingCustomerIdentityException.class);
    }

    private Jwt jwt(Map<String, Object> claims) {
        return new Jwt(
                "token",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T01:00:00Z"),
                Map.of("alg", "none"),
                claims
        );
    }
}
