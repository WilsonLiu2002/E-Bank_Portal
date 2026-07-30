package com.ebanking.transactions.security;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CustomerIdentityResolver {

    private static final List<String> CUSTOMER_ID_CLAIMS = List.of("customer_id", "customerId", "sub");

    public String resolve(Jwt jwt) {
        if (jwt == null) {
            throw new MissingCustomerIdentityException("Authenticated JWT is required");
        }
        return CUSTOMER_ID_CLAIMS.stream()
                .map(jwt::getClaimAsString)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElseThrow(() -> new MissingCustomerIdentityException(
                        "JWT must contain customer_id, customerId, or sub"));
    }
}
