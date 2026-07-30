package com.ebanking.transactions.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Instant;
import java.util.Map;

@Configuration
@Profile("local")
public class LocalJwtConfig {

    static final String LOCAL_TOKEN = "local-test-token";

    @Bean
    JwtDecoder jwtDecoder() {
        return token -> {
            if (!LOCAL_TOKEN.equals(token)) {
                throw new JwtException("Invalid local development token");
            }
            Instant now = Instant.now();
            return new Jwt(
                    token,
                    now,
                    now.plusSeconds(3600),
                    Map.of("alg", "none"),
                    Map.of("customer_id", "P-0123456789", "sub", "P-0123456789")
            );
        };
    }
}
