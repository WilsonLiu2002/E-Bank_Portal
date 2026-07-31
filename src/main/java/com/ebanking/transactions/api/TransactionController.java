package com.ebanking.transactions.api;

import com.ebanking.transactions.security.CustomerIdentityResolver;
import com.ebanking.transactions.service.TransactionQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions")
@Tag(name = "Transactions")
@SecurityScheme(name = "bearer-jwt", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
@Validated
public class TransactionController {

    private static final Logger log = LoggerFactory.getLogger(TransactionController.class);

    private final TransactionQueryService transactionQueryService;
    private final CustomerIdentityResolver customerIdentityResolver;

    public TransactionController(TransactionQueryService transactionQueryService,
                                 CustomerIdentityResolver customerIdentityResolver) {
        this.transactionQueryService = transactionQueryService;
        this.customerIdentityResolver = customerIdentityResolver;
    }

    @GetMapping
    @Operation(
            summary = "Return a paginated list of account transactions for the logged-on customer and month.",
            security = @SecurityRequirement(name = "bearer-jwt"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Transaction page returned"),
                    @ApiResponse(responseCode = "400", description = "Invalid request",
                            content = @Content(schema = @Schema(implementation = ApiErrorDto.class))),
                    @ApiResponse(responseCode = "401", description = "Missing or invalid JWT"),
                    @ApiResponse(responseCode = "403", description = "JWT does not contain a usable customer identity")
            }
    )
    public TransactionPageDto getTransactions(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @RequestParam
            @Pattern(regexp = "\\d{4}-\\d{2}", message = "month must use yyyy-MM format")
            String month,
            @RequestParam(defaultValue = "CHF")
            @Pattern(regexp = "[A-Z]{3}", message = "targetCurrency must be an ISO-4217 code")
            String targetCurrency,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        String customerId = customerIdentityResolver.resolve(jwt);
        Pageable pageable = PageRequest.of(page, size, Sort.by(
                Sort.Order.desc("valueDate"),
                Sort.Order.asc("transactionId")
        ));
        try (MDC.MDCCloseable ignored = MDC.putCloseable("customerId", customerId)) {
            log.info("Transaction page requested month={} targetCurrency={} page={} size={}",
                    month, targetCurrency, page, size);
            return transactionQueryService.findCustomerTransactions(customerId, month, targetCurrency, pageable);
        }
    }
}
