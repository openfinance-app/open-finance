package org.openfinance.dto;

/** Dependencies that block deletion, and budgets that will be removed with the category. */
public record CategoryDeletionImpact(
        boolean system,
        boolean hasChildren,
        long transactionCount,
        long splitCount,
        long recurringCount,
        long payeeCount,
        long budgetCount) {
    public boolean isDeletable() {
        return !system
                && !hasChildren
                && transactionCount == 0
                && splitCount == 0
                && recurringCount == 0
                && payeeCount == 0;
    }
}
