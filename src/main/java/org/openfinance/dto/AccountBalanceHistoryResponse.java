package org.openfinance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Dated native balances and their reporting/comparison values for account charts. */
public record AccountBalanceHistoryResponse(
        LocalDate date,
        BigDecimal balance,
        String currency,
        BigDecimal balanceInBaseCurrency,
        String baseCurrency,
        BigDecimal exchangeRate,
        boolean isConverted,
        BigDecimal balanceInSecondaryCurrency,
        String secondaryCurrency,
        BigDecimal secondaryExchangeRate) {}
