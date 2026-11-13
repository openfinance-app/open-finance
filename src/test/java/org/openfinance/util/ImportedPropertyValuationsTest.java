package org.openfinance.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.openfinance.entity.Asset;
import org.openfinance.entity.AssetPriceHistory;
import org.openfinance.entity.Transaction;

class ImportedPropertyValuationsTest {
    private static final LocalDate START = LocalDate.of(2024, 1, 1);
    private static final LocalDate TODAY = LocalDate.of(2024, 12, 31);

    @Test
    void preservesForeignQuoteCurrencyAndQuantitiesOnEachDate() {
        Asset asset = asset();
        List<Transaction> movements =
                List.of(
                        movement(1L, START, "1", "1000"),
                        movement(2L, START.plusMonths(5), "-0.5", "700"));
        List<AssetPriceHistory> prices =
                List.of(quote(START, "1100", "USD"), quote(START.plusMonths(2), "1400", "USD"));
        List<ImportedPropertyValuations.Point> history =
                ImportedPropertyValuations.history(asset, movements, prices, TODAY);
        assertThat(history)
                .containsExactly(
                        new ImportedPropertyValuations.Point(START, new BigDecimal("1100"), "USD"),
                        new ImportedPropertyValuations.Point(
                                START.plusMonths(2), new BigDecimal("1400"), "USD"),
                        new ImportedPropertyValuations.Point(
                                START.plusMonths(5), new BigDecimal("700.0"), "USD"),
                        new ImportedPropertyValuations.Point(
                                TODAY, new BigDecimal("1250.0"), "EUR"));
    }

    @Test
    void neverBackdatesFutureExecutionOrMarketPrices() {
        Asset asset = asset();
        Transaction movement = movement(1L, START, "1", "1000");
        movement.setInvestmentPrice(new BigDecimal("1200"));
        movement.setInvestmentPriceDate(START.plusMonths(2));
        List<ImportedPropertyValuations.Point> history =
                ImportedPropertyValuations.history(
                        asset,
                        List.of(movement),
                        List.of(quote(TODAY.plusDays(1), "9999", "EUR")),
                        TODAY);
        assertThat(history)
                .containsExactly(
                        new ImportedPropertyValuations.Point(START, new BigDecimal("1000"), "EUR"),
                        new ImportedPropertyValuations.Point(
                                START.plusMonths(2), new BigDecimal("1200"), "EUR"),
                        new ImportedPropertyValuations.Point(
                                TODAY, new BigDecimal("1250.0"), "EUR"));
    }

    @Test
    void backdatedExecutionQuoteDoesNotRevalueHoldingsBeforeTheMovement() {
        Asset asset = asset();
        asset.setImportOpeningQuantity(BigDecimal.ONE);
        asset.setImportOpeningCost(new BigDecimal("1000"));
        Transaction movement = movement(1L, START.plusMonths(2), "1", "1500");
        movement.setInvestmentPrice(new BigDecimal("1500"));
        movement.setInvestmentPriceDate(START.plusMonths(1));
        List<ImportedPropertyValuations.Point> history =
                ImportedPropertyValuations.history(asset, List.of(movement), List.of(), TODAY);
        assertThat(history)
                .containsExactly(
                        new ImportedPropertyValuations.Point(START, new BigDecimal("1000"), "EUR"),
                        new ImportedPropertyValuations.Point(
                                START.plusMonths(1), new BigDecimal("1000"), "EUR"),
                        new ImportedPropertyValuations.Point(
                                START.plusMonths(2), new BigDecimal("3000"), "EUR"),
                        new ImportedPropertyValuations.Point(
                                TODAY, new BigDecimal("1250.0"), "EUR"));
    }

    private Asset asset() {
        return Asset.builder()
                .id(1L)
                .currency("EUR")
                .purchaseDate(START)
                .importOpeningQuantity(BigDecimal.ZERO)
                .importOpeningCost(BigDecimal.ZERO)
                .quantity(new BigDecimal("0.5"))
                .currentPrice(new BigDecimal("2500"))
                .build();
    }

    private Transaction movement(Long id, LocalDate date, String quantity, String value) {
        return Transaction.builder()
                .id(id)
                .assetId(1L)
                .date(date)
                .currency("EUR")
                .investmentQuantity(new BigDecimal(quantity))
                .investmentValue(new BigDecimal(value))
                .build();
    }

    private AssetPriceHistory quote(LocalDate date, String value, String currency) {
        return AssetPriceHistory.builder()
                .priceDate(date)
                .price(new BigDecimal(value))
                .currency(currency)
                .source("SKROOGE")
                .build();
    }
}
