package com.ebanking.transactions.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalJwtConfigTest {

    private final JwtDecoder decoder = new LocalJwtConfig().jwtDecoder();

    @Test
    void acceptsFixedLocalDevelopmentToken() {
        Jwt jwt = decoder.decode("local-test-token");

        assertThat(jwt.getClaimAsString("customer_id")).isEqualTo("P-0123456789");
    }

    @Test
    void rejectsAnyOtherToken() {
        assertThatThrownBy(() -> decoder.decode("wrong-token"))
                .isInstanceOf(JwtException.class);
    }
}
