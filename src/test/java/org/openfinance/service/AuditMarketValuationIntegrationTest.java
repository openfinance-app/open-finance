package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.openfinance.dto.MarketQuote;
import org.openfinance.provider.MarketDataProvider;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;

/** Real encrypted persistence, with a deterministic external quote and FX failure. */
class AuditMarketValuationIntegrationTest extends AuditApiTestSupport {
    @MockBean MarketDataProvider provider;
    @SpyBean ExchangeRateService rates;

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
