package org.openfinance.entity;

/**
 * Classification of a financial movement linked to a liability, asset or real estate property, or
 * an interest credit paid into an account.
 *
 * <p>Mapped to {@code Transaction.movementType} since Task 2.
 */
public enum MovementType {
    DISBURSEMENT,
    REPAYMENT,
    INTEREST,
    INSURANCE,
    FEE,
    CAPITAL_IMPROVEMENT,
    MAINTENANCE,
    REVALUATION
}
