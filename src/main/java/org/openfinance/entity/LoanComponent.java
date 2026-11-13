package org.openfinance.entity;

/** Explicit loan allocation, independent of the user's spending-category hierarchy. */
public enum LoanComponent {
    PRINCIPAL,
    INTEREST,
    INSURANCE,
    FEE,
    OTHER
}
