package org.openfinance.controller;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.ConvertRequest;
import org.openfinance.dto.ConvertResponse;
import org.openfinance.dto.CurrencyResponse;
import org.openfinance.dto.ExchangeRateResponse;
import org.openfinance.entity.Currency;
import org.openfinance.repository.CurrencyRepository;
import org.openfinance.service.ExchangeRateService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for currency and exchange rate operations.
 *
 * <p>This controller provides endpoints for:
 *
 * <ul>
 *   <li>Listing supported currencies
 *   <li>Querying exchange rates between currency pairs
 *   <li>Converting amounts between currencies
 *   <li>Manually triggering exchange rate updates (admin only)
 * </ul>
 *
 * <p><strong>Base URL:</strong> {@code /api/v1/currencies}
 *
 * <p><strong>Authentication:</strong> All endpoints require JWT authentication via Bearer token.
 *
 * <p><strong>Multi-Currency Support:</strong> Supports 40+ currencies including:
 *
 * <ul>
 *   <li>Fiat currencies: USD, EUR, GBP, JPY, CHF, CAD, AUD, etc.
 *   <li>Cryptocurrencies: BTC, ETH, BNB, ADA, SOL, etc.
 * </ul>
 *
 * <p>Requirements: REQ-6.2 (Multi-Currency Support)
 *
 * @author Open-Finance Development Team
 * @since 1.0
 */
@RestController
@RequestMapping("/api/v1/currencies")
@RequiredArgsConstructor
@Validated
@Slf4j
public class CurrencyController {

    private final CurrencyRepository currencyRepository;
    private final ExchangeRateService exchangeRateService;
    private final MessageSource messageSource;

    /**
     * Lists all active currencies.
     *
     * <p><strong>Endpoint:</strong> {@code GET /api/v1/currencies}
     *
     * <p><strong>Request Example:</strong>
     *
     * <pre>
     * GET /api/v1/currencies
     * Authorization: Bearer eyJhbGc...
     * Accept-Language: fr
     * </pre>
     *
     * <p><strong>Response Example:</strong> HTTP 200 OK
     *
     * <pre>
     * [
     *   {
     *     "code": "USD",
     *     "name": "Dollar américain",
     *     "symbol": "$",
     *     "isActive": true,
     *     "nameKey": "currency.usd"
     *   },
     *   {
     *     "code": "EUR",
     *     "name": "Euro",
     *     "symbol": "€",
     *     "isActive": true,
     *     "nameKey": "currency.eur"
     *   }
     * ]
     * </pre>
     *
     * @return list of active currencies with localized names
     */
    @GetMapping
    public ResponseEntity<List<CurrencyResponse>> getActiveCurrencies() {
        Locale locale = LocaleContextHolder.getLocale();
        log.debug("Fetching all active currencies for locale: {}", locale);

        List<CurrencyResponse> currencies =
                currencyRepository.findByIsActiveTrueOrderByCodeAsc().stream()
                        .map(currency -> toCurrencyResponse(currency, locale))
                        .toList();

        log.info("Returned {} active currencies", currencies.size());
        return ResponseEntity.ok(currencies);
    }

    /**
     * Lists all currencies (including inactive ones).
     *
     * <p><strong>Endpoint:</strong> {@code GET /api/v1/currencies/all}
     *
     * <p><strong>Request Example:</strong>
     *
     * <pre>
     * GET /api/v1/currencies/all
     * Authorization: Bearer eyJhbGc...
     * Accept-Language: fr
     * </pre>
     *
     * <p><strong>Response Example:</strong> HTTP 200 OK
     *
     * <pre>
     * [
     *   {
     *     "code": "USD",
     *     "name": "Dollar américain",
     *     "symbol": "$",
     *     "isActive": true,
     *     "nameKey": "currency.usd"
     *   },
     *   {
     *     "code": "XAU",
     *     "name": "Or",
     *     "symbol": "XAU",
     *     "isActive": false,
     *     "nameKey": "currency.xau"
     *   }
     * ]
     * </pre>
     *
     * @return list of all currencies with localized names
     */
    @GetMapping("/all")
    public ResponseEntity<List<CurrencyResponse>> getAllCurrencies() {
        Locale locale = LocaleContextHolder.getLocale();
        log.debug("Fetching all currencies (including inactive) for locale: {}", locale);

        List<CurrencyResponse> currencies =
                currencyRepository.findAll().stream()
                        .map(currency -> toCurrencyResponse(currency, locale))
                        .toList();

        log.info("Returned {} total currencies", currencies.size());
        return ResponseEntity.ok(currencies);
    }

    /**
     * Gets the exchange rate between two currencies for a specific date.
     *
     * <p><strong>Endpoint:</strong> {@code GET /api/v1/currencies/exchange-rates}
     *
     * <p><strong>Query Parameters:</strong>
     *
     * <ul>
     *   <li>{@code from} - source currency code (required, e.g., "USD")
     *   <li>{@code to} - target currency code (required, e.g., "EUR")
     *   <li>{@code date} - rate date (optional, format: YYYY-MM-DD, defaults to today)
     * </ul>
     *
     * <p><strong>Request Example:</strong>
     *
     * <pre>
     * GET /api/v1/currencies/exchange-rates?from=USD&to=EUR&date=2024-01-15
     * Authorization: Bearer eyJhbGc...
     * </pre>
     *
     * <p><strong>Response Example:</strong> HTTP 200 OK
     *
     * <pre>
     * {
     *   "baseCurrency": "USD",
     *   "targetCurrency": "EUR",
     *   "rate": 0.85,
     *   "inverseRate": 1.1764705882,
     *   "rateDate": "2024-01-15",
     *   "source": "Yahoo Finance"
     * }
     * </pre>
     *
     * <p><strong>Error Response:</strong> HTTP 404 Not Found
     *
     * <pre>
     * {
     *   "error": "Exchange rate not found for USD -> EUR on 2024-01-15"
     * }
     * </pre>
     *
     * @param from the source currency code
     * @param to the target currency code
     * @param date the rate date (optional)
     * @return exchange rate information
     */
    @GetMapping("/exchange-rates")
    public ResponseEntity<ExchangeRateResponse> getExchangeRate(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate date) {

        LocalDate rateDate = (date != null) ? date : LocalDate.now();
        log.debug("Fetching exchange rate: {} -> {} on {}", from, to, rateDate);

        BigDecimal rate = exchangeRateService.getExchangeRate(from, to, rateDate);

        // Build response with calculated inverse rate
        ExchangeRateResponse response =
                ExchangeRateResponse.builder()
                        .baseCurrency(from)
                        .targetCurrency(to)
                        .rate(rate)
                        .inverseRate(BigDecimal.ONE.divide(rate, 8, RoundingMode.HALF_UP))
                        .rateDate(rateDate)
                        .source("Yahoo Finance")
                        .build();

        log.info("Returned exchange rate: {} -> {} = {} on {}", from, to, rate, rateDate);
        return ResponseEntity.ok(response);
    }

    /**
     * Gets the latest exchange rate between two currencies.
     *
     * <p><strong>Endpoint:</strong> {@code GET /api/v1/currencies/exchange-rates/latest}
     *
     * <p><strong>Query Parameters:</strong>
     *
     * <ul>
     *   <li>{@code from} - source currency code (required, e.g., "USD")
     *   <li>{@code to} - target currency code (required, e.g., "EUR")
     * </ul>
     *
     * <p><strong>Request Example:</strong>
     *
     * <pre>
     * GET /api/v1/currencies/exchange-rates/latest?from=USD&to=EUR
     * Authorization: Bearer eyJhbGc...
     * </pre>
     *
     * <p><strong>Response Example:</strong> HTTP 200 OK
     *
     * <pre>
     * {
     *   "baseCurrency": "USD",
     *   "targetCurrency": "EUR",
     *   "rate": 0.85,
     *   "inverseRate": 1.1764705882,
     *   "rateDate": "2024-02-01",
     *   "source": "Yahoo Finance"
     * }
     * </pre>
     *
     * @param from the source currency code
     * @param to the target currency code
     * @return latest exchange rate information
     */
    @GetMapping("/exchange-rates/latest")
    public ResponseEntity<ExchangeRateResponse> getLatestExchangeRate(
            @RequestParam String from, @RequestParam String to) {

        log.debug("Fetching latest exchange rate: {} -> {}", from, to);

        BigDecimal rate = exchangeRateService.getExchangeRate(from, to, LocalDate.now());

        // Build response with calculated inverse rate
        ExchangeRateResponse response =
                ExchangeRateResponse.builder()
                        .baseCurrency(from)
                        .targetCurrency(to)
                        .rate(rate)
                        .inverseRate(BigDecimal.ONE.divide(rate, 8, RoundingMode.HALF_UP))
                        .rateDate(LocalDate.now())
                        .source("Yahoo Finance")
                        .build();

        log.info(
                "Returned latest exchange rate: {} -> {} = {} on {}",
                from,
                to,
                rate,
                LocalDate.now());
        return ResponseEntity.ok(response);
    }

    /**
     * Converts an amount from one currency to another.
     *
     * <p><strong>Endpoint:</strong> {@code POST /api/v1/currencies/convert}
     *
     * <p><strong>Request Example:</strong>
     *
     * <pre>
     * POST /api/v1/currencies/convert
     * Authorization: Bearer eyJhbGc...
     * Content-Type: application/json
     *
     * {
     *   "amount": 100.00,
     *   "fromCurrency": "USD",
     *   "toCurrency": "EUR",
     *   "date": "2024-01-15"
     * }
     * </pre>
     *
     * <p><strong>Response Example:</strong> HTTP 200 OK
     *
     * <pre>
     * {
     *   "originalAmount": 100.00,
     *   "fromCurrency": "USD",
     *   "convertedAmount": 85.00,
     *   "toCurrency": "EUR",
     *   "exchangeRate": 0.85,
     *   "date": "2024-01-15"
     * }
     * </pre>
     *
     * <p><strong>Validation Errors:</strong> HTTP 400 Bad Request
     *
     * <pre>
     * {
     *   "error": "Amount must be greater than 0"
     * }
     * </pre>
     *
     * @param request the conversion request
     * @return conversion result with converted amount
     */
    @PostMapping("/convert")
    public ResponseEntity<ConvertResponse> convertCurrency(
            @Valid @RequestBody ConvertRequest request) {
        log.debug(
                "Converting {} {} to {}",
                request.amount(),
                request.fromCurrency(),
                request.toCurrency());

        // Use current date if not specified
        LocalDate conversionDate = (request.date() != null) ? request.date() : LocalDate.now();

        // Perform conversion
        BigDecimal convertedAmount;
        BigDecimal exchangeRate;

        if (request.date() != null) {
            // Historical conversion
            convertedAmount =
                    exchangeRateService.convert(
                            request.amount(),
                            request.fromCurrency(),
                            request.toCurrency(),
                            conversionDate);
            exchangeRate =
                    exchangeRateService.getExchangeRate(
                            request.fromCurrency(), request.toCurrency(), conversionDate);
        } else {
            // Current conversion
            convertedAmount =
                    exchangeRateService.convert(
                            request.amount(), request.fromCurrency(), request.toCurrency());
            exchangeRate =
                    exchangeRateService.getExchangeRate(
                            request.fromCurrency(), request.toCurrency(), LocalDate.now());
        }

        ConvertResponse response =
                ConvertResponse.builder()
                        .originalAmount(request.amount())
                        .fromCurrency(request.fromCurrency())
                        .convertedAmount(convertedAmount)
                        .toCurrency(request.toCurrency())
                        .exchangeRate(exchangeRate)
                        .date(conversionDate)
                        .build();

        log.info(
                "Converted {} {} to {} {} (rate: {}) on {}",
                request.amount(),
                request.fromCurrency(),
                convertedAmount,
                request.toCurrency(),
                exchangeRate,
                conversionDate);

        return ResponseEntity.ok(response);
    }

    /**
     * Manually triggers exchange rate update from external API (admin only).
     *
     * <p><strong>Endpoint:</strong> {@code POST /api/v1/currencies/exchange-rates/update}
     *
     * <p><strong>Authorization:</strong> Requires ADMIN role.
     *
     * <p><strong>Request Example:</strong>
     *
     * <pre>
     * POST /api/v1/currencies/exchange-rates/update
     * Authorization: Bearer eyJhbGc...
     * </pre>
     *
     * <p><strong>Response Example:</strong> HTTP 200 OK
     *
     * <pre>
     * {
     *   "message": "Exchange rates updated successfully",
     *   "updatedCount": 45
     * }
     * </pre>
     *
     * <p><strong>Error Response:</strong> HTTP 403 Forbidden
     *
     * <pre>
     * {
     *   "error": "Access denied. Admin role required."
     * }
     * </pre>
     *
     * @return update result with count of updated rates
     */
    @PostMapping("/exchange-rates/update")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UpdateRatesResponse> updateExchangeRates() {
        log.info("Manual exchange rate update triggered by admin");

        int updatedCount = exchangeRateService.updateExchangeRates();

        UpdateRatesResponse response =
                new UpdateRatesResponse("Exchange rates updated successfully", updatedCount);

        log.info("Exchange rates updated: {} rates fetched", updatedCount);
        return ResponseEntity.ok(response);
    }

    /**
     * Converts Currency entity to CurrencyResponse DTO with localized name.
     *
     * <p>For currencies with a {@code nameKey}, the name is resolved via {@link MessageSource} in
     * the given locale, with the stored name as fallback.
     *
     * @param currency the currency entity
     * @param locale the locale for name resolution
     * @return currency response DTO with localized name
     */
    private CurrencyResponse toCurrencyResponse(Currency currency, Locale locale) {
        String localizedName = currency.getName(); // Default to stored name

        // Resolve localized name if nameKey is present
        if (currency.getNameKey() != null && !currency.getNameKey().isBlank()) {
            localizedName =
                    messageSource.getMessage(
                            currency.getNameKey(),
                            null,
                            currency.getName(), // Fallback to stored name
                            locale);
        }

        return CurrencyResponse.builder()
                .code(currency.getCode())
                .name(localizedName)
                .symbol(currency.getSymbol())
                .isActive(currency.getIsActive())
                .nameKey(currency.getNameKey())
                .build();
    }

    /** Response DTO for exchange rate update operation. */
    public record UpdateRatesResponse(String message, int updatedCount) {}
}
