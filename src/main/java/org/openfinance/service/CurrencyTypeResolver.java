package org.openfinance.service;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.entity.Currency;
import org.openfinance.entity.CurrencyType;
import org.openfinance.repository.CurrencyRepository;
import org.openfinance.util.MoneyPrecision;
import org.springframework.stereotype.Service;

/**
 * Single cached source of truth for a currency's {@link CurrencyType} and its calculation
 * precision. Backs {@code ExchangeRateService.isCryptocurrency()} and interest/precision rounding.
 *
 * <p>Holds an in-memory {@code code -> type} snapshot that is lazily loaded on first use and can be
 * explicitly refreshed via {@link #reload()} (called after the crypto list is refreshed).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CurrencyTypeResolver {

    private final CurrencyRepository currencyRepository;

    /** code (upper-case) -> type snapshot; {@code null} until first load. */
    private volatile Map<String, CurrencyType> typeByCode;

    /** Rebuilds the in-memory snapshot from the database. Thread-safe. */
    public synchronized void reload() {
        Map<String, CurrencyType> map = new HashMap<>();
        for (Currency c : currencyRepository.findAll()) {
            if (c.getCode() != null && c.getType() != null) {
                map.put(c.getCode().toUpperCase(Locale.ROOT), c.getType());
            }
        }
        typeByCode = map;
        log.debug("CurrencyTypeResolver reloaded: {} currencies", map.size());
    }

    private Map<String, CurrencyType> snapshot() {
        Map<String, CurrencyType> local = typeByCode;
        if (local == null) {
            reload();
            local = typeByCode;
        }
        return local;
    }

    /**
     * @return {@code true} if the code is classified {@link CurrencyType#CRYPTO}; {@code false} for
     *     fiat, unknown, or {@code null} codes.
     */
    public boolean isCrypto(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        return snapshot().get(code.trim().toUpperCase(Locale.ROOT)) == CurrencyType.CRYPTO;
    }

    /**
     * Calculation precision, independent of display preferences. Unknown/crypto codes retain 18
     * digits.
     */
    public int decimalsFor(String code) {
        if (code == null || code.isBlank()) {
            return 18;
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        return isCrypto(normalized) ? 18 : MoneyPrecision.scale(normalized);
    }
}
