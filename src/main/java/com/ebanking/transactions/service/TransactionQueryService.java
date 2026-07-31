package com.ebanking.transactions.service;

import com.ebanking.transactions.api.MoneyDto;
import com.ebanking.transactions.api.PageMetadataDto;
import com.ebanking.transactions.api.TransactionDto;
import com.ebanking.transactions.api.TransactionPageDto;
import com.ebanking.transactions.domain.MoneyAccountTransaction;
import com.ebanking.transactions.domain.MoneyAccountTransactionRepository;
import com.ebanking.transactions.exchange.MoneyConversionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

@Service
public class TransactionQueryService {

    private static final Logger log = LoggerFactory.getLogger(TransactionQueryService.class);

    private final MoneyAccountTransactionRepository transactionRepository;
    private final MoneyConversionService moneyConversionService;

    public TransactionQueryService(MoneyAccountTransactionRepository transactionRepository,
                                   MoneyConversionService moneyConversionService) {
        this.transactionRepository = transactionRepository;
        this.moneyConversionService = moneyConversionService;
    }

    @Transactional(readOnly = true)
    public TransactionPageDto findCustomerTransactions(String customerId,
                                                       String month,
                                                       String targetCurrency,
                                                       Pageable pageable) {
        YearMonth yearMonth = parse(month);
        String normalizedTargetCurrency = targetCurrency.toUpperCase(Locale.ROOT);
        Page<MoneyAccountTransaction> page = transactionRepository
                .findByCustomerIdAndValueDateGreaterThanEqualAndValueDateLessThan(
                        customerId,
                        yearMonth.atDay(1),
                        yearMonth.plusMonths(1).atDay(1),
                        pageable
                );

        BigDecimal totalCredit = BigDecimal.ZERO;
        BigDecimal totalDebit = BigDecimal.ZERO;
        for (MoneyAccountTransaction transaction : page.getContent()) {
            BigDecimal converted = moneyConversionService.convert(
                    transaction.getAmount().abs(),
                    transaction.getCurrency(),
                    normalizedTargetCurrency
            );
            if (transaction.getAmount().signum() >= 0) {
                totalCredit = totalCredit.add(converted);
            } else {
                totalDebit = totalDebit.add(converted);
            }
        }

        List<TransactionDto> transactions = page.getContent().stream()
                .map(transaction -> new TransactionDto(
                        transaction.getTransactionId(),
                        new MoneyDto(transaction.getAmount(), transaction.getCurrency()),
                        transaction.getAccountIban(),
                        transaction.getValueDate(),
                        transaction.getDescription()))
                .toList();

        TransactionPageDto response = new TransactionPageDto(
                transactions,
                new MoneyDto(totalCredit, normalizedTargetCurrency),
                new MoneyDto(totalDebit, normalizedTargetCurrency),
                new PageMetadataDto(page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages())
        );
        log.info("Transaction page resolved month={} targetCurrency={} returnedRows={} totalElements={}",
                yearMonth, normalizedTargetCurrency, transactions.size(), page.getTotalElements());
        return response;
    }

    private YearMonth parse(String month) {
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException exception) {
            throw new InvalidMonthException(month);
        }
    }
}
