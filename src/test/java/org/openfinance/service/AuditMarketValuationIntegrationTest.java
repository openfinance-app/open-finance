package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.openfinance.dto.HistoricalPrice;
import org.openfinance.dto.MarketQuote;
import org.openfinance.entity.ExchangeRate;
import org.openfinance.provider.MarketDataProvider;
import org.openfinance.repository.ExchangeRateRepository;
import org.openfinance.scheduler.MarketDataScheduler;
import org.openfinance.security.EncryptionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;

/** Real encrypted persistence, with a deterministic external quote and FX failure. */
class AuditMarketValuationIntegrationTest extends AuditApiTestSupport {
    @MockBean MarketDataProvider provider;
    @SpyBean ExchangeRateService rates;
    @Autowired MarketDataScheduler scheduler;
    @Autowired ExchangeRateRepository rateRepository;
    @Autowired org.springframework.cache.CacheManager cacheManager;

    @Test
    void scheduledRefreshUsesTheOwnersCachedKeyWithoutARequest() throws Exception {
        long asset = stock("BTC-USD", "PURCHASE", "EUR", 50000);
        when(provider.getQuotes(java.util.List.of("BTC-USD")))
                .thenReturn(
                        java.util.List.of(
                                MarketQuote.builder()
                                        .symbol("BTC-USD")
                                        .currency("USD")
                                        .price(new BigDecimal("81875.92"))
                                        .build()));
        org.mockito.Mockito.doReturn(new BigDecimal("73112.80"))
                .when(rates)
                .convert(new BigDecimal("81875.92"), "USD", "EUR");
        EncryptionContext.clear();
        org.springframework.security.core.context.SecurityContextHolder.clearContext();

        scheduler.triggerManualUpdate();

        assertThat(EncryptionContext.getKey()).isNull();
        money(json("GET", "/assets/" + asset, null, owner, 200), "currentPrice", "73112.80");
    }

    @Test
    void historicalQuotePricesUseTheirOwnCurrencyAndEachDatesExchangeRate() throws Exception {
        LocalDate first = LocalDate.now().minusDays(1);
        LocalDate second = LocalDate.now();
        HistoricalPrice price =
                HistoricalPrice.builder()
                        .symbol("BTC-USD")
                        .currency("USD")
                        .date(first)
                        .open(new BigDecimal("80000"))
                        .high(new BigDecimal("83000"))
                        .low(new BigDecimal("79000"))
                        .close(new BigDecimal("81875.92"))
                        .volume(123L)
                        .build();
        when(provider.getHistoricalPrices("BTC-USD", first, second))
                .thenReturn(java.util.List.of(price, price.toBuilder().date(second).build()));
        storeRate(first, "0.9");
        storeRate(second, "0.8");
        cacheManager.getCache("exchangeRates").clear();
        String path = "/market/history?symbol=BTC-USD&startDate=" + first + "&endDate=" + second;

        JsonNode converted = json("GET", path + "&currency=EUR", null, owner, 200);

        assertThat(converted.get(0).path("currency").asText()).isEqualTo("EUR");
        money(converted.get(0), "close", "73688.328");
        money(converted.get(0), "open", "72000");
        money(converted.get(0), "high", "74700");
        money(converted.get(0), "low", "71100");
        money(converted.get(1), "close", "65500.736");
        assertThat(converted.get(0).path("volume").asLong()).isEqualTo(123L);
        assertThat(converted.get(0).path("adjustedClose").isNull()).isTrue();
        JsonNode original = json("GET", path, null, owner, 200);
        assertThat(original.get(0).path("currency").asText()).isEqualTo("USD");
        money(original.get(0), "close", "81875.92");

        cacheManager.getCache("exchangeRates").clear();
        doThrow(new IllegalStateException("Historical FX unavailable"))
                .when(rates)
                .getExchangeRateQuote("USD", "EUR", first);
        json("GET", path + "&currency=EUR", null, owner, 503);
    }

    private void storeRate(LocalDate date, String rate) {
        ExchangeRate stored =
                rateRepository
                        .findByBaseCurrencyAndTargetCurrencyAndRateDate("USD", "EUR", date)
                        .orElseGet(
                                () ->
                                        ExchangeRate.builder()
                                                .baseCurrency("USD")
                                                .targetCurrency("EUR")
                                                .rateDate(date)
                                                .source("asset-history-regression")
                                                .build());
        stored.setRate(new BigDecimal(rate));
        rateRepository.save(stored);
    }

    @Test
    void historyRejectsUnknownCurrencyInsteadOfRelabelingThePrice() throws Exception {
        LocalDate today = LocalDate.now();
        when(provider.getHistoricalPrices("BTC-USD", today, today))
                .thenReturn(
                        java.util.List.of(
                                HistoricalPrice.builder()
                                        .symbol("BTC-USD")
                                        .date(today)
                                        .close(new BigDecimal("81875.92"))
                                        .build()));
        json(
                "GET",
                "/market/history?symbol=BTC-USD&startDate="
                        + today
                        + "&endDate="
                        + today
                        + "&currency=EUR",
                null,
                owner,
                503);
    }

    @Test
    void auditMissingFxPreservesLastKnownAssetPrice() throws Exception {
        jdbc.update("UPDATE users SET base_currency = 'EUR' WHERE id = ?", owner.id());
        long asset =
                json(
                                "POST",
                                "/assets",
                                data(
                                        "name",
                                        "Audit stock",
                                        "type",
                                        "STOCK",
                                        "symbol",
                                        "AUDIT",
                                        "quantity",
                                        2,
                                        "purchasePrice",
                                        80,
                                        "currentPrice",
                                        80,
                                        "currency",
                                        "EUR",
                                        "purchaseDate",
                                        START.toString()),
                                owner,
                                201)
                        .path("id")
                        .asLong();
        when(provider.getQuote("AUDIT"))
                .thenReturn(
                        MarketQuote.builder()
                                .symbol("AUDIT")
                                .currency("USD")
                                .price(new BigDecimal("100"))
                                .build());
        doThrow(new IllegalStateException("Audit FX unavailable"))
                .when(rates)
                .convert(new BigDecimal("100"), "USD", "EUR");
        JsonNode update =
                json("POST", "/market/assets/" + asset + "/update-price", null, owner, 503);
        JsonNode persisted = json("GET", "/assets/" + asset, null, owner, 200);
        System.out.println("AUDIT_FX_REFRESH update=" + update + " persisted=" + persisted);
        assertThat(persisted.path("currentPrice").decimalValue()).isEqualByComparingTo("80");
    }

    @Test
    void auditQuoteRefreshDoesNotValueAnUnacquiredPlannedHolding() throws Exception {
        jdbc.update("UPDATE users SET base_currency = 'EUR' WHERE id = ?", owner.id());
        long cash = account(owner);
        long asset =
                json(
                                "POST",
                                "/assets",
                                data(
                                        "name",
                                        "Planned stock",
                                        "type",
                                        "STOCK",
                                        "symbol",
                                        "PLAN",
                                        "accountId",
                                        cash,
                                        "acquisitionType",
                                        "PLANNED",
                                        "quantity",
                                        1,
                                        "purchasePrice",
                                        100,
                                        "currentPrice",
                                        0,
                                        "currency",
                                        "EUR",
                                        "purchaseDate",
                                        LocalDate.now().plusMonths(1).toString()),
                                owner,
                                201)
                        .path("id")
                        .asLong();
        when(provider.getQuote("PLAN"))
                .thenReturn(
                        MarketQuote.builder()
                                .symbol("PLAN")
                                .currency("EUR")
                                .price(new BigDecimal("100"))
                                .build());
        json("POST", "/market/assets/" + asset + "/update-price", null, owner, 400);
        JsonNode persisted = json("GET", "/assets/" + asset, null, owner, 200);
        JsonNode cashAfter = json("GET", "/accounts/" + cash, null, owner, 200);
        System.out.println(
                "AUDIT_PLANNED_REFRESH currentPrice="
                        + persisted.path("currentPrice")
                        + " accountBalance="
                        + cashAfter.path("balance"));
        money(persisted, "currentPrice", "0");
    }

    @Test
    void auditLinkedForeignAssetDoesNotFabricateAccountBalanceWithoutFx() throws Exception {
        jdbc.update("UPDATE users SET base_currency = 'EUR' WHERE id = ?", owner.id());
        long cash = account(owner);
        org.mockito.Mockito.doReturn(new BigDecimal("0.5"))
                .when(rates)
                .getExchangeRate("USD", "EUR", null);
        org.mockito.Mockito.doReturn(new BigDecimal("50"))
                .when(rates)
                .convert(
                        org.mockito.ArgumentMatchers.any(BigDecimal.class),
                        org.mockito.ArgumentMatchers.eq("USD"),
                        org.mockito.ArgumentMatchers.eq("EUR"));
        json(
                "POST",
                "/assets",
                data(
                        "name",
                        "Foreign holding",
                        "type",
                        "STOCK",
                        "symbol",
                        "FOREIGN",
                        "accountId",
                        cash,
                        "quantity",
                        1,
                        "purchasePrice",
                        100,
                        "currentPrice",
                        100,
                        "currency",
                        "USD",
                        "purchaseDate",
                        START.toString()),
                owner,
                201);
        JsonNode before = json("GET", "/accounts/" + cash, null, owner, 200);
        money(before, "balance", "1050");
        doThrow(new IllegalStateException("Audit FX unavailable"))
                .when(rates)
                .convert(
                        org.mockito.ArgumentMatchers.any(BigDecimal.class),
                        org.mockito.ArgumentMatchers.eq("USD"),
                        org.mockito.ArgumentMatchers.eq("EUR"));
        json("GET", "/accounts/" + cash, null, owner, 503);
    }

    @Test
    void bulkRefreshPreservesFailedQuotesAndSkipsPlannedAssets() throws Exception {
        long failed = stock("FAIL", "PURCHASE", "EUR", 80);
        long unknown = stock("UNKNOWN", "PURCHASE", "EUR", 70);
        long valid = stock("GOOD", "PURCHASE", "EUR", 50);
        long planned = stock("PLAN", "PLANNED", "EUR", 0);
        when(provider.getQuotes(org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(
                        java.util.List.of(
                                MarketQuote.builder()
                                        .symbol("FAIL")
                                        .currency("USD")
                                        .price(new BigDecimal("100"))
                                        .build(),
                                MarketQuote.builder()
                                        .symbol("UNKNOWN")
                                        .price(new BigDecimal("100"))
                                        .build(),
                                MarketQuote.builder()
                                        .symbol("GOOD")
                                        .currency("EUR")
                                        .price(new BigDecimal("90"))
                                        .build(),
                                MarketQuote.builder()
                                        .symbol("PLAN")
                                        .currency("EUR")
                                        .price(new BigDecimal("100"))
                                        .build()));
        doThrow(new IllegalStateException("Audit FX unavailable"))
                .when(rates)
                .convert(new BigDecimal("100"), "USD", "EUR");
        JsonNode refreshed = json("POST", "/market/assets/refresh-all", null, owner, 200);
        assertThat(refreshed.path("updated").asInt()).isEqualTo(1);
        money(json("GET", "/assets/" + failed, null, owner, 200), "currentPrice", "80");
        money(json("GET", "/assets/" + unknown, null, owner, 200), "currentPrice", "70");
        money(json("GET", "/assets/" + valid, null, owner, 200), "currentPrice", "90");
        money(json("GET", "/assets/" + planned, null, owner, 200), "currentPrice", "0");
    }

    private long stock(String symbol, String acquisition, String currency, int price)
            throws Exception {
        return json(
                        "POST",
                        "/assets",
                        data(
                                "name",
                                symbol,
                                "type",
                                "STOCK",
                                "symbol",
                                symbol,
                                "quantity",
                                1,
                                "purchasePrice",
                                50,
                                "currentPrice",
                                price,
                                "currency",
                                currency,
                                "acquisitionType",
                                acquisition,
                                "purchaseDate",
                                START.toString()),
                        owner,
                        201)
                .path("id")
                .asLong();
    }
}
