package org.openfinance.service;

import lombok.RequiredArgsConstructor;
import org.openfinance.config.ExchangeRateProperties;
import org.openfinance.entity.User;
import org.openfinance.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Single source of truth for the application's default currency and for resolving a user's
 * effective base currency.
 *
 * <p>The application-wide default is bound from {@code application.exchange-rates.base-currency}
 * (see {@link ExchangeRateProperties}) — there is no longer any hardcoded {@code "USD"}/{@code
 * "EUR"} fallback scattered across services, controllers, schedulers, entities or the frontend.
 *
 * <p>Resolution rules:
 *
 * <ul>
 *   <li>A user's explicitly configured {@link User#getBaseCurrency() base currency} always wins.
 *   <li>When the user has no (or a blank) base currency, the application default is used.
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class DefaultCurrencyProvider {

    private final ExchangeRateProperties exchangeRateProperties;
    private final UserRepository userRepository;

    /**
     * Returns the application-wide default currency (ISO 4217), configured via {@code
     * application.exchange-rates.base-currency}.
     *
     * @return the default currency code, never {@code null}/blank
     */
    public String getDefaultCurrency() {
        return exchangeRateProperties.getBaseCurrency();
    }

    /**
     * Returns {@code currency} when it is a non-blank value, otherwise the application default.
     *
     * @param currency a currency code that may be {@code null} or blank
     * @return the supplied currency, or the application default when absent
     */
    public String resolve(String currency) {
        return StringUtils.hasText(currency) ? currency : getDefaultCurrency();
    }

    /**
     * Resolves the effective base currency for a user: the user's configured base currency when
     * set, otherwise the application default. Never returns {@code null}.
     *
     * @param userId the user ID (may be {@code null})
     * @return the user's base currency or the application default
     */
    @Transactional(readOnly = true)
    public String resolveForUser(Long userId) {
        if (userId == null) {
            return getDefaultCurrency();
        }
        return userRepository
                .findById(userId)
                .map(User::getBaseCurrency)
                .filter(StringUtils::hasText)
                .orElseGet(this::getDefaultCurrency);
    }
}
