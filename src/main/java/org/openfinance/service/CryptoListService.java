package org.openfinance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.config.CryptoListProperties;
import org.openfinance.dto.CryptoCurrencyInfo;
import org.openfinance.entity.Currency;
import org.openfinance.entity.CurrencyType;
import org.openfinance.entity.ExchangeRate;
import org.openfinance.provider.CryptoListProvider;
import org.openfinance.repository.CurrencyRepository;
import org.openfinance.repository.ExchangeRateRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refreshes the set of supported cryptocurrencies from the configured {@link CryptoListProvider}
 * chain, upserting them into {@code currencies} (type = CRYPTO) and seeding USD&lt;-&gt;crypto
 * rates from the same bulk response. Never deletes/deactivates existing rows and never overwrites
 * fiat.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CryptoListService {

    private final List<CryptoListProvider> providers;
    private final CryptoListProperties properties;
    private final CurrencyRepository currencyRepository;
    private final ExchangeRateRepository exchangeRateRepository;
    private final CurrencyTypeResolver currencyTypeResolver;

    /**
     * Fetches the top-N cryptocurrencies (with fallback), upserts them, seeds their rates, and
     * refreshes the {@link CurrencyTypeResolver} cache.
     *
     * @return the number of cryptocurrencies inserted or updated
     */
    @Transactional
    @CacheEvict(value = "exchangeRates", allEntries = true)
    public int refresh() {
        if (!properties.isEnabled()) {
            log.info("Crypto-list refresh skipped: feature disabled");
            return 0;
        }
        List<CryptoCurrencyInfo> coins = fetchWithFallback();
        if (coins.isEmpty()) {
            log.warn("Crypto-list refresh produced no coins from any provider");
            return 0;
        }
        coins = deduplicateByCode(coins);
        int upserted = upsertCurrencies(coins);
        seedRates(coins);
        currencyTypeResolver.reload();
        log.info("Crypto-list refresh complete: {} cryptocurrencies upserted", upserted);
        return upserted;
    }

    /** Builds the ordered provider chain: primary first, then (if enabled) the rest. */
    private List<CryptoListProvider> orderedChain() {
        List<CryptoListProvider> chain = new ArrayList<>();
        providers.stream()
                .filter(p -> p.isEnabled() && p.name().equalsIgnoreCase(properties.getProvider()))
                .findFirst()
                .ifPresent(chain::add);
        if (properties.isFallbackEnabled()) {
            for (CryptoListProvider p : providers) {
                if (p.isEnabled() && !chain.contains(p)) {
                    chain.add(p);
                }
            }
        }
        return chain;
    }

    private List<CryptoCurrencyInfo> fetchWithFallback() {
        for (CryptoListProvider provider : orderedChain()) {
            try {
                List<CryptoCurrencyInfo> coins =
                        provider.fetchTopCryptocurrencies(properties.getLimit());
                if (coins != null && !coins.isEmpty()) {
                    log.info("Fetched {} coins from provider '{}'", coins.size(), provider.name());
                    return coins;
                }
                log.warn("Provider '{}' returned no coins", provider.name());
            } catch (Exception e) {
                log.warn("Provider '{}' failed", provider.name(), e);
            }
        }
        return List.of();
    }

    /**
     * Removes coins with duplicate codes, keeping the first occurrence. Providers return coins in
     * market-cap-rank order, so the highest-ranked coin wins a code collision. This keeps the
     * upsert count accurate and prevents duplicate {@code (base,target,rate_date)} rate rows in one
     * batch.
     */
    private List<CryptoCurrencyInfo> deduplicateByCode(List<CryptoCurrencyInfo> coins) {
        Map<String, CryptoCurrencyInfo> byCode = new LinkedHashMap<>();
        for (CryptoCurrencyInfo coin : coins) {
            byCode.putIfAbsent(coin.code(), coin);
        }
        return new ArrayList<>(byCode.values());
    }

    private int upsertCurrencies(List<CryptoCurrencyInfo> coins) {
        int count = 0;
        for (CryptoCurrencyInfo coin : coins) {
            Optional<Currency> existing = currencyRepository.findByCode(coin.code());
            if (existing.isPresent()) {
                Currency current = existing.get();
                if (current.getType() == CurrencyType.FIAT) {
                    log.debug("Skipping crypto '{}' — code already exists as fiat", coin.code());
                    continue;
                }
                current.setName(coin.name());
                current.setSymbol(coin.symbol());
                currencyRepository.save(current);
            } else {
                currencyRepository.save(
                        Currency.builder()
                                .code(coin.code())
                                .name(coin.name())
                                .symbol(coin.symbol())
                                .isActive(true)
                                .type(CurrencyType.CRYPTO)
                                .build());
            }
            count++;
        }
        return count;
    }

    private void seedRates(List<CryptoCurrencyInfo> coins) {
        LocalDate today = LocalDate.now();
        List<ExchangeRate> rates = new ArrayList<>();
        for (CryptoCurrencyInfo coin : coins) {
            BigDecimal price = coin.priceUsd();
            if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            boolean isFiatCollision =
                    currencyRepository
                            .findByCode(coin.code())
                            .map(c -> c.getType() == CurrencyType.FIAT)
                            .orElse(false);
            if (isFiatCollision) {
                continue;
            }
            addRateIfAbsent(
                    rates,
                    "USD",
                    coin.code(),
                    BigDecimal.ONE.divide(price, 8, RoundingMode.HALF_UP),
                    today);
            addRateIfAbsent(rates, coin.code(), "USD", price, today);
        }
        if (!rates.isEmpty()) {
            exchangeRateRepository.saveAll(rates);
        }
    }

    private void addRateIfAbsent(
            List<ExchangeRate> acc, String base, String target, BigDecimal rate, LocalDate date) {
        boolean exists =
                exchangeRateRepository
                        .findByBaseCurrencyAndTargetCurrencyAndRateDate(base, target, date)
                        .isPresent();
        if (!exists) {
            acc.add(
                    ExchangeRate.builder()
                            .baseCurrency(base)
                            .targetCurrency(target)
                            .rate(rate)
                            .rateDate(date)
                            .source("crypto-list")
                            .build());
        }
    }
}
