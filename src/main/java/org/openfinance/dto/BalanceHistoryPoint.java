package org.openfinance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO representing a balance history point for an account.
 *
 * <p>Used to track account balance over time for visualization in charts.
 *
 * <p>Requirement REQ-2.6.1.2: Account Balance Tracking - Historical snapshots
 */
public record BalanceHistoryPoint(LocalDate date, BigDecimal balance) {}
