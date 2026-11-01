package org.openfinance.util;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.openfinance.entity.Asset;
import org.openfinance.entity.Transaction;

/** Reconstruct positions using units and moving-average acquisition cost. */
public final class InvestmentHoldings {
    private InvestmentHoldings() {}

    public record Position(
            BigDecimal quantity,
            BigDecimal cost,
            BigDecimal remainingReceivedValue,
            BigDecimal realizedGain) {}

    public static void update(Asset asset, List<Transaction> transactions) {
        Position position = at(asset, transactions, LocalDate.MAX);
        asset.setQuantity(position.quantity());
        asset.setRemainingReceivedValue(position.remainingReceivedValue());
        asset.setPurchasePrice(
                position.quantity().signum() == 0
                        ? BigDecimal.ZERO
                        : position.cost()
                                .divide(position.quantity(), 18, java.math.RoundingMode.HALF_UP));
    }

    public static Position at(Asset asset, List<Transaction> transactions, LocalDate date) {
        if (asset.getPurchaseDate().isAfter(date))
            return new Position(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        BigDecimal quantity =
                asset.getImportOpeningQuantity() == null
                        ? BigDecimal.ZERO
                        : asset.getImportOpeningQuantity();
        BigDecimal cost =
                asset.getImportOpeningCost() == null
                        ? BigDecimal.ZERO
                        : asset.getImportOpeningCost();
        BigDecimal giftContribution = asset.getReceivedValue();
        BigDecimal realizedGain =
                asset.getAcquisitionType() == org.openfinance.entity.AcquisitionType.GIFT
                                && giftContribution == null
                        ? null
                        : BigDecimal.ZERO;
        List<Transaction> movements =
                transactions.stream()
                        .filter(
                                tx ->
                                        asset.getId().equals(tx.getAssetId())
                                                && tx.getInvestmentQuantity() != null
                                                && !Boolean.TRUE.equals(tx.getIsDeleted())
                                                && !tx.getDate().isAfter(date))
                        .sorted(
                                Comparator.comparing(Transaction::getDate)
                                        // Preserve posting order within a day: a later buy must
                                        // not change the cost basis released by an earlier sale.
                                        .thenComparing(Transaction::getId))
                        .toList();
        for (Transaction movement : movements) {
            BigDecimal change = movement.getInvestmentQuantity();
            if (quantity.add(change).signum() < 0)
                throw org.openfinance.exception.InvalidTransactionException.investment(
                        "oversell",
                        "Cannot remove "
                                + change.abs().toPlainString()
                                + " units of "
                                + asset.getName()
                                + " on "
                                + movement.getDate()
                                + ": only "
                                + quantity.toPlainString()
                                + " units are available",
                        asset.getName(),
                        change.abs().stripTrailingZeros().toPlainString(),
                        quantity.stripTrailingZeros().toPlainString(),
                        movement.getDate().toString());
            if (change.signum() > 0) {
                cost = cost.add(acquisitionCost(asset, movement));
            } else if (quantity.signum() > 0) {
                if (movement.isInvestmentCash() && realizedGain != null) {
                    BigDecimal basis =
                            cost.add(giftContribution == null ? BigDecimal.ZERO : giftContribution);
                    BigDecimal released =
                            basis.multiply(change.abs()).divide(quantity, MathContext.DECIMAL128);
                    realizedGain =
                            realizedGain.add(acquisitionCost(asset, movement).subtract(released));
                }
                cost = cost.multiply(quantity.add(change)).divide(quantity, MathContext.DECIMAL128);
                if (giftContribution != null) {
                    giftContribution =
                            giftContribution
                                    .multiply(quantity.add(change))
                                    .divide(quantity, MathContext.DECIMAL128);
                }
            }
            quantity = quantity.add(change);
            if (quantity.signum() == 0) cost = BigDecimal.ZERO;
        }
        return new Position(quantity, cost, giftContribution, realizedGain);
    }

    private static BigDecimal acquisitionCost(Asset asset, Transaction movement) {
        if (movement.getInvestmentValue() != null) return movement.getInvestmentValue();
        if (asset.getCurrency().equals(movement.getCurrency())) return movement.getAmount();
        if (asset.getCurrency().equals(movement.getOriginalCurrency())
                && movement.getOriginalAmount() != null) {
            return movement.getOriginalAmount();
        }
        throw new IllegalStateException(
                "Investment acquisition currency does not match the holding");
    }
}
