package org.openfinance.dto;

import java.math.BigDecimal;

/** Structured monetary fields also carry lossless decimal text in HTTP responses. */
public record CashFlowResponse(BigDecimal income, BigDecimal expenses, BigDecimal netCashFlow) {}
