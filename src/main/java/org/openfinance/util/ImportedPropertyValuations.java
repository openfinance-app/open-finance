package org.openfinance.util;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.TreeSet;
import org.openfinance.entity.Asset;
import org.openfinance.entity.AssetPriceHistory;
import org.openfinance.entity.Transaction;

/** Preserves the holding's dated quantities and prices as total property valuations. */
public final class ImportedPropertyValuations {
    private ImportedPropertyValuations() {}

    public record Point(LocalDate date, BigDecimal value, String currency) {}

    public static List<Point> history(
            Asset asset,
            List<Transaction> movements,
            List<AssetPriceHistory> prices,
            LocalDate today) {
        NavigableMap<LocalDate, Point> quotes = new TreeMap<>();
        NavigableMap<LocalDate, Point> fallbacks = new TreeMap<>();
        TreeSet<LocalDate> dates = new TreeSet<>();
        dates.add(asset.getPurchaseDate());
        for (AssetPriceHistory price : prices) {
            if (!"TRADE".equals(price.getSource()))
                quotes.put(
                        price.getPriceDate(),
                        new Point(price.getPriceDate(), price.getPrice(), price.getCurrency()));
            dates.add(price.getPriceDate());
        }
        NavigableMap<LocalDate, List<Point>> availableFallbacks =
                fallbackEvents(asset, movements, dates);
        List<Point> result = new ArrayList<>();
        for (LocalDate date : dates) {
            // An execution quote becomes available when both its quote date and movement date
            // have occurred, matching reconstructed holding history before conversion.
            for (Point fallback : availableFallbacks.getOrDefault(date, List.of()))
                fallbacks.putIfAbsent(fallback.date(), fallback);
            if (date.isBefore(asset.getPurchaseDate()) || date.isAfter(today)) continue;
            InvestmentHoldings.Position position = InvestmentHoldings.at(asset, movements, date);
            java.util.Map.Entry<LocalDate, Point> quote = quotes.floorEntry(date);
            if (quote == null) quote = fallbacks.floorEntry(date);
            result.add(
                    quote == null
                            ? new Point(date, position.cost(), asset.getCurrency())
                            : new Point(
                                    date,
                                    position.quantity().multiply(quote.getValue().value()),
                                    quote.getValue().currency()));
        }
        // Conversion is a reclassification at today's recorded value, never a new purchase.
        result.removeIf(point -> point.date().equals(today));
        result.add(new Point(today, asset.getTotalValue(), asset.getCurrency()));
        return result;
    }

    private static NavigableMap<LocalDate, List<Point>> fallbackEvents(
            Asset asset, List<Transaction> movements, TreeSet<LocalDate> dates) {
        NavigableMap<LocalDate, List<Point>> availableFallbacks = new TreeMap<>();
        for (Transaction tx : movements) {
            dates.add(tx.getDate());
            if (tx.getInvestmentQuantity() == null || tx.getInvestmentQuantity().signum() == 0)
                continue;
            LocalDate date =
                    tx.getInvestmentPriceDate() == null
                            ? tx.getDate()
                            : tx.getInvestmentPriceDate();
            BigDecimal value = tx.getInvestmentValue();
            if (value == null && asset.getCurrency().equals(tx.getCurrency()))
                value = tx.getAmount();
            BigDecimal price = tx.getInvestmentPrice();
            if (price == null && value != null)
                price = value.divide(tx.getInvestmentQuantity().abs(), MathContext.DECIMAL128);
            if (price != null && price.signum() >= 0)
                availableFallbacks
                        .computeIfAbsent(
                                date.isBefore(tx.getDate()) ? tx.getDate() : date,
                                key -> new ArrayList<>())
                        .add(new Point(date, price, asset.getCurrency()));
            dates.add(date);
        }
        if (asset.getLastUpdated() != null && asset.getCurrentPrice() != null) {
            LocalDate date = asset.getLastUpdated().toLocalDate();
            availableFallbacks
                    .computeIfAbsent(date, key -> new ArrayList<>())
                    .add(new Point(date, asset.getCurrentPrice(), asset.getCurrency()));
            dates.add(date);
        }
        return availableFallbacks;
    }
}
