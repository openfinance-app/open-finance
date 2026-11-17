package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.ImportedTransaction;
import org.openfinance.dto.SkroogeImportMetadata;
import org.openfinance.dto.SkroogeImportMetadata.SkroogeHolding;
import org.openfinance.dto.SkroogeImportMetadata.SkroogePrice;
import org.openfinance.entity.Asset;
import org.openfinance.entity.AssetType;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionType;
import org.openfinance.exception.InvalidTransactionException;
import org.openfinance.repository.AssetRepository;
import org.openfinance.repository.TransactionRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("Reimporting investments preserves edited movements and newer valuations")
class ImportedInvestmentReconciliationTest {
    private static final LocalDate DATE = LocalDate.of(2025, 1, 15);
    @Mock private AssetRepository assets;
    @Mock private TransactionRepository transactions;
    @Mock private AccountCurrencyService currency;
    @Mock private AssetPriceHistoryWriter prices;
    @InjectMocks private ImportedInvestmentServiceImpl service;

    private Asset holding() {
        return Asset.builder()
                .id(11L)
                .userId(7L)
                .accountId(3L)
                .type(AssetType.STOCK)
                .name("Fund")
                .currency("EUR")
                .importSource("skrooge:unit:9")
                .quantity(BigDecimal.ONE)
                .purchasePrice(BigDecimal.TEN)
                .currentPrice(new BigDecimal("20"))
                .purchaseDate(DATE)
                .lastUpdated(org.openfinance.util.ReportingClock.startOfDay(DATE))
                .importOpeningQuantity(BigDecimal.ONE)
                .importOpeningCost(BigDecimal.TEN)
                .build();
    }

    private ImportedTransaction reviewed() {
        return ImportedTransaction.builder()
                .sourceAccountId(2L)
                .investmentUnitId(9L)
                .referenceNumber("row-1")
                .transactionDate(DATE)
                .amount(new BigDecimal("-10"))
                .currency("EUR")
                .investmentQuantity(BigDecimal.ONE)
                .build();
    }

    private Transaction stored() {
        return Transaction.builder()
                .id(4L)
                .accountId(3L)
                .userId(7L)
                .date(DATE)
                .externalReference("row-1")
                .type(TransactionType.EXPENSE)
                .amount(BigDecimal.TEN)
                .currency("EUR")
                .assetId(11L)
                .investmentQuantity(BigDecimal.ONE)
                .build();
    }

    private SkroogeHolding source(
            BigDecimal openingQuantity,
            BigDecimal openingPrice,
            BigDecimal currentPrice,
            LocalDate priceDate) {
        return new SkroogeHolding(
                2L,
                9L,
                "Fund",
                "FND",
                AssetType.STOCK,
                "EUR",
                openingQuantity,
                openingPrice,
                DATE,
                currentPrice,
                priceDate,
                List.of(new SkroogePrice(DATE, BigDecimal.TEN, "EUR")));
    }

    private void reconcile(SkroogeHolding source, List<ImportedTransaction> rows) {
        service.importHoldings(
                SkroogeImportMetadata.builder().holding(source).build(), rows, Map.of(2L, 3L), 7L);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "edited",
                "other-user",
                "unreviewed",
                "missing-quantity",
                "changed-date",
                "changed-amount",
                "changed-direction",
                "wrong-account",
                "wrong-unit",
                "wrong-original-currency",
                "missing-original-amount",
                "changed-original-amount"
            })
    void doesNotReconnectAMovementWhoseReviewedIdentityOrEconomicsHaveChanged(String mismatch) {
        Transaction stored = stored();
        ImportedTransaction reviewed = reviewed();
        switch (mismatch) {
            case "edited" -> stored.setInvestmentEdited(true);
            case "other-user" -> stored.setUserId(8L);
            case "unreviewed" -> stored.setExternalReference("other-row");
            case "missing-quantity" -> reviewed.setInvestmentQuantity(null);
            case "changed-date" -> stored.setDate(DATE.plusDays(1));
            case "changed-amount" -> stored.setAmount(new BigDecimal("10.01"));
            case "changed-direction" -> stored.setType(TransactionType.INCOME);
            case "wrong-account" -> reviewed.setSourceAccountId(99L);
            case "wrong-unit" -> reviewed.setInvestmentUnitId(99L);
            case "wrong-original-currency" -> {
                stored.setCurrency("USD");
                stored.setOriginalCurrency("GBP");
            }
            case "missing-original-amount" -> {
                stored.setCurrency("USD");
                stored.setOriginalCurrency("EUR");
            }
            case "changed-original-amount" -> {
                stored.setCurrency("USD");
                stored.setOriginalCurrency("EUR");
                stored.setOriginalAmount(new BigDecimal("10.01"));
            }
            default -> throw new AssertionError(mismatch);
        }
        when(transactions.findActiveByAccountId(3L)).thenReturn(List.of(stored));
        reconcile(source(BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.TEN, DATE), List.of(reviewed));
        verify(transactions, never()).save(any());
        verifyNoInteractions(assets, currency, prices);
    }

    @Test
    void ignoresAHoldingWhoseSourceAccountWasNotImported() {
        service.importHoldings(
                SkroogeImportMetadata.builder()
                        .holding(source(BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN, DATE))
                        .build(),
                List.of(reviewed()),
                Map.of(),
                7L);
        verifyNoInteractions(assets, transactions, currency, prices);
    }

    @ParameterizedTest
    @ValueSource(strings = {"same", "converted", "income", "duplicate-source-row"})
    void reconnectsUneditedMovementsUsingTheirOriginalCurrencyAndKeepsTheFirstReviewedRow(
            String variant) {
        Transaction stored = stored();
        ImportedTransaction reviewed = reviewed();
        Asset asset = holding();
        if (variant.equals("converted")) {
            stored.setCurrency("USD");
            stored.setAmount(new BigDecimal("11.50"));
            stored.setOriginalCurrency("EUR");
            stored.setOriginalAmount(new BigDecimal("10.000"));
        }
        if (variant.equals("income")) {
            stored.setType(TransactionType.INCOME);
            reviewed.setAmount(BigDecimal.TEN);
            reviewed.setInvestmentQuantity(BigDecimal.ONE.negate());
            asset.setImportOpeningQuantity(new BigDecimal("2"));
            asset.setImportOpeningCost(new BigDecimal("20"));
        }
        when(transactions.findActiveByAccountId(3L)).thenReturn(List.of(stored));
        when(assets.findByAccountId(3L)).thenReturn(List.of(asset));
        when(assets.save(asset)).thenReturn(asset);
        when(assets.findByIdAndUserId(11L, 7L)).thenReturn(Optional.of(asset));
        ImportedTransaction duplicate = reviewed();
        duplicate.setInvestmentQuantity(new BigDecimal("99"));
        reconcile(
                source(BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN, DATE),
                variant.equals("duplicate-source-row")
                        ? List.of(reviewed, duplicate)
                        : List.of(reviewed));
        assertThat(stored.getInvestmentQuantity())
                .isEqualByComparingTo(reviewed.getInvestmentQuantity());
        assertThat(stored.getAssetId()).isEqualTo(11L);
        verify(currency).book(stored, 7L);
        verify(transactions).save(stored);
        assertThat(asset.getQuantity()).isEqualByComparingTo(variant.equals("income") ? "1" : "2");
        verify(prices)
                .recordAll(
                        org.mockito.ArgumentMatchers.eq(7L),
                        org.mockito.ArgumentMatchers.eq(asset),
                        org.mockito.ArgumentMatchers.argThat(
                                values ->
                                        values.size() == 1
                                                && values.getFirst()
                                                                .getClose()
                                                                .compareTo(BigDecimal.TEN)
                                                        == 0),
                        org.mockito.ArgumentMatchers.eq("SKROOGE"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"unchanged", "different-asset", "missing-quantity", "different-quantity"})
    void reimportOfAConvertedPropertyIsReadOnlyUnlessItsOriginalMovementsChanged(String variant) {
        Asset asset = holding();
        asset.setType(AssetType.REAL_ESTATE);
        Transaction stored = stored();
        if (variant.equals("different-asset")) stored.setAssetId(99L);
        if (variant.equals("missing-quantity")) stored.setInvestmentQuantity(null);
        if (variant.equals("different-quantity")) stored.setInvestmentQuantity(new BigDecimal("2"));
        when(transactions.findActiveByAccountId(3L)).thenReturn(List.of(stored));
        when(assets.findByAccountId(3L)).thenReturn(List.of(asset));
        if (variant.equals("unchanged"))
            reconcile(
                    source(BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN, DATE),
                    List.of(reviewed()));
        else
            assertThatThrownBy(
                            () ->
                                    reconcile(
                                            source(
                                                    BigDecimal.ONE,
                                                    BigDecimal.TEN,
                                                    BigDecimal.TEN,
                                                    DATE),
                                            List.of(reviewed())))
                    .isInstanceOf(InvalidTransactionException.class);
        verify(assets, never()).save(any());
        verify(transactions, never()).save(any());
        verifyNoInteractions(currency, prices);
        assertThat(asset.getCurrentPrice()).isEqualByComparingTo("20");
    }

    @ParameterizedTest
    @CsvSource({"-1,20", "0,10", "1,10"})
    void staleHoldingQuotesCannotReplaceAMoreRecentValuation(int offset, String expected) {
        Asset asset = holding();
        when(transactions.findActiveByAccountId(3L)).thenReturn(List.of());
        when(assets.findByAccountId(3L)).thenReturn(List.of(asset));
        when(assets.save(asset)).thenReturn(asset);
        when(assets.findByIdAndUserId(11L, 7L)).thenReturn(Optional.of(asset));
        reconcile(
                source(BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN, DATE.plusDays(offset)),
                List.of());
        assertThat(asset.getCurrentPrice()).isEqualByComparingTo(expected);
        assertThat(asset.getLastUpdated())
                .isEqualTo(
                        org.openfinance.util.ReportingClock.startOfDay(
                                DATE.plusDays(Math.max(offset, 0))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"opening", "current", "currency"})
    void rejectsIncompleteOrCurrencyConflictingHoldingsBeforeWritingPrices(String invalid) {
        when(transactions.findActiveByAccountId(3L)).thenReturn(List.of());
        if (invalid.equals("currency")) {
            Asset asset = holding();
            asset.setCurrency("USD");
            when(assets.findByAccountId(3L)).thenReturn(List.of(asset));
        }
        assertThatThrownBy(
                        () ->
                                reconcile(
                                        source(
                                                BigDecimal.ONE,
                                                invalid.equals("opening") ? null : BigDecimal.TEN,
                                                invalid.equals("current") ? null : BigDecimal.TEN,
                                                DATE),
                                        List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        verify(assets, never()).save(any());
        verifyNoInteractions(prices, currency);
    }

    @ParameterizedTest
    @ValueSource(strings = {"newer", "same", "older", "undated", "currency-conflict", "converted"})
    void tradePreparationPreservesNewerQuotesAndRejectsConvertedOrConflictingPositions(
            String variant) throws Exception {
        Asset asset = holding();
        String security = "ISIN:TEST";
        asset.setImportSource(
                "security:"
                        + java.util.HexFormat.of()
                                .formatHex(
                                        java.security.MessageDigest.getInstance("SHA-256")
                                                .digest(
                                                        security.getBytes(
                                                                java.nio.charset.StandardCharsets
                                                                        .UTF_8))));
        if (variant.equals("converted")) asset.setType(AssetType.REAL_ESTATE);
        if (variant.equals("currency-conflict")) asset.setCurrency("USD");
        if (variant.equals("undated")) asset.setLastUpdated(null);
        Transaction stored = stored();
        stored.setDate(
                DATE.plusDays(variant.equals("newer") ? 1 : variant.equals("older") ? -1 : 0));
        ImportedTransaction source = reviewed();
        source.setInvestmentSecurity(security);
        source.setInvestmentPrice(new BigDecimal("12"));
        Asset unrelated = holding();
        unrelated.setUserId(8L);
        Asset otherSource = holding();
        otherSource.setImportSource("different");
        when(assets.findByAccountId(3L)).thenReturn(List.of(unrelated, otherSource, asset));
        if (variant.equals("converted") || variant.equals("currency-conflict")) {
            assertThatThrownBy(() -> service.prepare(stored, source))
                    .isInstanceOf(RuntimeException.class);
            verify(assets, never()).save(any());
            verifyNoInteractions(prices);
            return;
        }
        when(assets.save(asset)).thenReturn(asset);
        service.prepare(stored, source);
        assertThat(asset.getCurrentPrice())
                .isEqualByComparingTo(variant.equals("older") ? "20" : "12");
        assertThat(asset.getPurchaseDate())
                .isEqualTo(variant.equals("older") ? DATE.minusDays(1) : DATE);
        verify(prices).record(7L, asset, stored.getDate(), new BigDecimal("12"), "EUR", "TRADE");
    }
}
