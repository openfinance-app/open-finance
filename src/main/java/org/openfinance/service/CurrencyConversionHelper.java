package org.openfinance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.entity.User;
import org.openfinance.repository.UserRepository;
import org.springframework.stereotype.Component;

/**
 * Single implementation of the base + secondary multi-currency conversion previously duplicated,
 * near-identically, in {@code AccountService}, {@code AssetService}, {@code LiabilityService},
 * {@code RealEstateService} and {@code TransactionService}'s {@code populateConversionFields(...)}.
 *
 * <p>The five services differed only in which DTO they populated (and the {@code Balance*}/{@code
 * Value*}/{@code Amount*} setter names), plus the transaction path's lack of a secondary currency
 * and its 4-decimal rounding. This helper owns the actual conversion logic and returns a neutral
 * {@link ConversionResult}; each service keeps a thin mapping onto its own DTO. This removes the
 * duplication without forcing a shared interface onto the response DTOs.
 *
 * <p>Requirements: REQ-4.1–REQ-4.6 (base + secondary currency display).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CurrencyConversionHelper {

    private final UserRepository userRepository;
    private final DefaultCurrencyProvider defaultCurrencyProvider;
    private final ExchangeRateService exchangeRateService;

    /**
     * Neutral result of a currency conversion, mapped by each caller onto its own DTO.
     *
     * @param baseCurrency the user's effective base currency (never {@code null})
     * @param amountInBaseCurrency the amount in base currency (native amount when not converted)
     * @param exchangeRate native→base rate, or {@code null} when not converted
     * @param converted whether an actual FX conversion to the base currency happened
     * @param secondaryCurrency the user's secondary currency when configured, else {@code null}
     * @param amountInSecondaryCurrency the amount in secondary currency, or {@code null}
     * @param secondaryExchangeRate native→secondary rate, or {@code null}
     */
    public record ConversionResult(
            String baseCurrency,
            BigDecimal amountInBaseCurrency,
            BigDecimal exchangeRate,
            boolean converted,
            String secondaryCurrency,
            BigDecimal amountInSecondaryCurrency,
            BigDecimal secondaryExchangeRate) {}

    /**
     * Converts {@code nativeAmount} from {@code nativeCurrency} to the user's base (and optionally
     * secondary) currency.
     *
     * @param userId the owning user (may be {@code null} → application default currency)
     * @param nativeCurrency the amount's own currency
     * @param nativeAmount the amount to convert (may be {@code null})
     * @param includeSecondary whether to also convert to the user's secondary currency
     * @param scale if non-{@code null}, rounds the base amount and rate to this scale (HALF_UP)
     * @param entityLabel a short noun ("account"/"asset"/…) used only in warn logs
     * @return the conversion outcome; never {@code null}
     */
    public ConversionResult convert(
            Long userId,
            String nativeCurrency,
            BigDecimal nativeAmount,
            boolean includeSecondary,
            Integer scale,
            String entityLabel) {

        User user = userId != null ? userRepository.findById(userId).orElse(null) : null;
        String baseCurrency =
                defaultCurrencyProvider.resolve(user != null ? user.getBaseCurrency() : null);
        String secCurrency = includeSecondary && user != null ? user.getSecondaryCurrency() : null;

        // --- Base conversion ---
        BigDecimal amountInBase;
        BigDecimal rate = null;
        boolean converted;
        boolean needsConversion = nativeCurrency != null && !nativeCurrency.equals(baseCurrency);
        if (!needsConversion || nativeAmount == null) {
            amountInBase = nativeAmount;
            converted = false;
        } else {
            try {
                BigDecimal r =
                        exchangeRateService.getExchangeRate(nativeCurrency, baseCurrency, null);
                BigDecimal c =
                        exchangeRateService.convert(nativeAmount, nativeCurrency, baseCurrency);
                if (scale != null) {
                    c = c.setScale(scale, RoundingMode.HALF_UP);
                    r = r.setScale(scale, RoundingMode.HALF_UP);
                }
                amountInBase = c;
                rate = r;
                converted = true;
            } catch (Exception e) {
                log.warn(
                        "Currency conversion failed for {} (user={}, {}->{}) – falling back to native: {}",
                        entityLabel,
                        userId,
                        nativeCurrency,
                        baseCurrency,
                        e.getMessage());
                amountInBase = nativeAmount;
                converted = false;
            }
        }

        // --- Secondary conversion (Requirement REQ-4.x) ---
        String resultSecCurrency = null;
        BigDecimal secAmount = null;
        BigDecimal secRate = null;
        if (secCurrency != null && !secCurrency.isBlank()) {
            // Secondary currency is always echoed back when configured, even if conversion is
            // skipped (native match) or fails — the frontend then omits the secondary line.
            resultSecCurrency = secCurrency;
            if (nativeCurrency != null
                    && !nativeCurrency.equals(secCurrency)
                    && nativeAmount != null) {
                try {
                    secRate =
                            exchangeRateService.getExchangeRate(nativeCurrency, secCurrency, null);
                    secAmount =
                            exchangeRateService.convert(nativeAmount, nativeCurrency, secCurrency);
                } catch (Exception e) {
                    log.warn(
                            "Secondary currency conversion failed for {} (user={}, {}->{}) – omitting: {}",
                            entityLabel,
                            userId,
                            nativeCurrency,
                            secCurrency,
                            e.getMessage());
                    secRate = null;
                    secAmount = null;
                }
            }
        }

        return new ConversionResult(
                baseCurrency, amountInBase, rate, converted, resultSecCurrency, secAmount, secRate);
    }
}
