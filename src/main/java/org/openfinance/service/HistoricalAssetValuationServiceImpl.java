package org.openfinance.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.HistoricalPrice;
import org.openfinance.entity.Asset;
import org.openfinance.entity.Transaction;
import org.openfinance.exception.MarketDataException;
import org.openfinance.repository.AssetPriceHistoryRepository;
import org.openfinance.util.InvestmentHoldings;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Reconstruct quantities separately from market value; never use a later quote. */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class HistoricalAssetValuationServiceImpl implements HistoricalAssetValuationService {
    private final AssetPriceHistoryRepository prices;
    private final AssetPriceHistoryWriter writer;
    private final MarketDataService marketData;
    private final ExchangeRateService exchangeRates;
    private final Cache<Request, List<HistoricalPrice>> fetched =
            Caffeine.newBuilder().maximumSize(256).expireAfterWrite(Duration.ofMinutes(15)).build();

    @Override
    public void preparePrices(Asset asset, LocalDate from, LocalDate to) {
        LocalDate start =
                asset.getPurchaseDate() != null && asset.getPurchaseDate().isAfter(from)
                        ? asset.getPurchaseDate()
                        : from;
        if (start.isAfter(to)) return;
        List<LocalDate> dates = new ArrayList<>(List.of(start, to));
        for (YearMonth month = YearMonth.from(start);
                !month.atEndOfMonth().isAfter(to);
                month = month.plusMonths(1)) {
            dates.add(month.atEndOfMonth());
        }
        loadQuotes(asset, dates);
    }

    @Override
    public BigDecimal valueAt(
            Asset asset, List<Transaction> transactions, LocalDate date, String currency) {
        if (asset.getPurchaseDate() != null && asset.getPurchaseDate().isAfter(date))
            return BigDecimal.ZERO;
        InvestmentHoldings.Position position = position(asset, transactions, date);
        if (position.quantity().signum() == 0) return BigDecimal.ZERO;
        NavigableMap<LocalDate, Quote> quotes = loadQuotes(asset, List.of(date));
        if (quotes.floorEntry(date) == null) addDatedFallbacks(quotes, asset, transactions, date);
        Map.Entry<LocalDate, Quote> selected = quotes.floorEntry(date);
        if (selected != null) {
            Quote quote = selected.getValue();
            return exchangeRates.convert(
                    position.quantity().multiply(quote.price()), quote.currency(), currency, date);
        }
        // No market or execution price exists at/before this date. Keep the recorded
        // acquisition valuation as an estimate, never substitute today's market quote.
        log.warn(
                "No dated market price for asset {} on {}; using recorded acquisition value",
                asset.getId(),
                date);
        return exchangeRates.convert(position.cost(), asset.getCurrency(), currency, date);
    }

    private NavigableMap<LocalDate, Quote> loadQuotes(Asset asset, List<LocalDate> dates) {
        NavigableMap<LocalDate, Quote> quotes = new TreeMap<>();
        prices.findByAssetIdAndUserIdOrderByPriceDateAsc(asset.getId(), asset.getUserId()).stream()
                .filter(row -> !"TRADE".equals(row.getSource()))
                .forEach(
                        row ->
                                quotes.put(
                                        row.getPriceDate(),
                                        new Quote(
                                                row.getPrice(),
                                                row.getCurrency(),
                                                AssetPriceHistoryWriter.priority(
                                                        row.getSource()))));
        if (dates.stream().anyMatch(date -> !quotes.containsKey(date))
                && asset.getSymbol() != null
                && !asset.getSymbol().isBlank()) {
            List<HistoricalPrice> history = fetch(asset);
            history.stream()
                    .filter(HistoricalAssetValuationServiceImpl::valid)
                    .forEach(
                            price ->
                                    quotes.merge(
                                            price.getDate(),
                                            new Quote(
                                                    price.getClose(),
                                                    price.getCurrency(),
                                                    AssetPriceHistoryWriter.priority("MARKET")),
                                            (stored, fetched) ->
                                                    stored.priority() > fetched.priority()
                                                            ? stored
                                                            : fetched));
        }
        return quotes;
    }

    private InvestmentHoldings.Position position(
            Asset asset, List<Transaction> transactions, LocalDate date) {
        if (asset.getImportSource() != null || asset.getImportOpeningQuantity() != null)
            return InvestmentHoldings.at(asset, transactions, date);
        BigDecimal quantity = asset.getQuantity() == null ? BigDecimal.ZERO : asset.getQuantity();
        return new InvestmentHoldings.Position(
                quantity,
                quantity.multiply(
                        asset.getPurchasePrice() == null
                                ? BigDecimal.ZERO
                                : asset.getPurchasePrice()),
                BigDecimal.ZERO,
                BigDecimal.ZERO);
    }

    private List<HistoricalPrice> fetch(Asset asset) {
        LocalDate from =
                asset.getPurchaseDate() == null
                        ? LocalDate.now().minusYears(10)
                        : asset.getPurchaseDate().minusDays(7);
        Request request =
                new Request(
                        asset.getUserId(), asset.getId(), asset.getSymbol(), from, LocalDate.now());
        return fetched.get(
                request,
                key -> {
                    try {
                        List<HistoricalPrice> history =
                                List.copyOf(
                                        marketData.getHistoricalPrices(
                                                key.symbol(), key.from(), key.to()));
                        // Persist only once per fetch, before any snapshots are rebuilt.
                        if (!history.isEmpty())
                            writer.recordAll(asset.getUserId(), asset, history, "MARKET");
                        return history;
                    } catch (MarketDataException | IllegalArgumentException ex) {
                        log.warn(
                                "Historical prices unavailable for asset {}: {}",
                                asset.getId(),
                                ex.getMessage());
                        return List.of();
                    }
                });
    }

    private void addDatedFallbacks(
            NavigableMap<LocalDate, Quote> quotes,
            Asset asset,
            List<Transaction> transactions,
            LocalDate date) {
        for (Transaction tx : transactions) {
            if (!asset.getId().equals(tx.getAssetId())
                    || Boolean.TRUE.equals(tx.getIsDeleted())
                    || tx.getInvestmentQuantity() == null
                    || tx.getInvestmentQuantity().signum() == 0
                    || tx.getDate().isAfter(date)) continue;
            LocalDate priceDate =
                    tx.getInvestmentPriceDate() == null
                            ? tx.getDate()
                            : tx.getInvestmentPriceDate();
            BigDecimal price = tx.getInvestmentPrice();
            if (price == null) {
                BigDecimal value = tx.getInvestmentValue();
                if (value == null && asset.getCurrency().equals(tx.getCurrency()))
                    value = tx.getAmount();
                if (value == null) continue;
                price = value.divide(tx.getInvestmentQuantity().abs(), MathContext.DECIMAL128);
            }
            if (!priceDate.isAfter(date) && price.signum() >= 0)
                quotes.putIfAbsent(priceDate, new Quote(price, asset.getCurrency(), 1));
        }
        if (asset.getLastUpdated() != null
                && asset.getCurrentPrice() != null
                && !asset.getLastUpdated().toLocalDate().isAfter(date)) {
            quotes.putIfAbsent(
                    asset.getLastUpdated().toLocalDate(),
                    new Quote(asset.getCurrentPrice(), asset.getCurrency(), 1));
        }
    }

    private static boolean valid(HistoricalPrice price) {
        return price.getDate() != null
                && price.getClose() != null
                && price.getClose().signum() >= 0
                && price.getCurrency() != null
                && !price.getCurrency().isBlank();
    }

    private record Quote(BigDecimal price, String currency, int priority) {}

    private record Request(
            Long userId, Long assetId, String symbol, LocalDate from, LocalDate to) {}
}
