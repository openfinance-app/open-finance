package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.openfinance.entity.ExchangeRate;
import org.openfinance.repository.ExchangeRateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;

/** Exercises both persisted legs through the authenticated transfer API. */
class TransferConversionIntegrationTest extends AuditApiTestSupport {
    @Autowired private ExchangeRateRepository rates;
    @Autowired private CacheManager caches;
    private final Map<LocalDate, BigDecimal> previousRates = new HashMap<>();

    @AfterEach
    void restoreRates() {
        previousRates.forEach(
                (date, previous) -> {
                    ExchangeRate row =
                            rates.findByBaseCurrencyAndTargetCurrencyAndRateDate("USD", "EUR", date)
                                    .orElseThrow();
                    if (previous == null) rates.delete(row);
                    else {
                        row.setRate(previous);
                        rates.saveAndFlush(row);
                    }
                });
        caches.getCache("exchangeRates").clear();
    }

    @Test
    void preservesBothBookingsOnMetadataEditAndUsesRecordedRateForAmountEdit() throws Exception {
        setRate(START, "0.8");
        setRate(LocalDate.now(), "0.8");
        long source = account(owner);
        long destination = dollarAccount();
        Map<String, Object> creation =
                data(
                        "accountId",
                        source,
                        "toAccountId",
                        destination,
                        "type",
                        "TRANSFER",
                        "amount",
                        "100",
                        "currency",
                        "EUR",
                        "date",
                        START.toString());
        JsonNode debit = json("POST", "/transactions/transfer", creation, owner, 201);
        money(debit, "transferAmount", "125");
        JsonNode credit = credit(destination);
        money(credit, "amount", "125");
        money(credit, "originalAmount", "100");
        assertThat(credit.path("originalCurrency").asText()).isEqualTo("EUR");
        money(credit, "conversionRate", "1.25");
        money(credit, "amountInBaseCurrency", "100");

        setRate(START, "0.5");
        Map<String, Object> edit =
                data(
                        "fromAccountId",
                        source,
                        "toAccountId",
                        destination,
                        "amount",
                        "100",
                        "currency",
                        "EUR",
                        "date",
                        START.toString(),
                        "description",
                        "Metadata only",
                        "isReconciled",
                        true);
        String path = "/transactions/transfers/" + debit.path("transferId").asText();
        json("PUT", path, edit, owner, 200);
        money(credit(destination), "amount", "125");
        money(json("GET", "/accounts/" + source, null, owner, 200), "ownBalance", "900");
        money(json("GET", "/accounts/" + destination, null, owner, 200), "ownBalance", "125");

        edit.put("amount", "120");
        json("PUT", path, edit, owner, 200);
        money(credit(destination), "amount", "150");
        money(credit(destination), "originalAmount", "120");
        money(credit(destination), "conversionRate", "1.25");
        money(json("GET", "/accounts/" + source, null, owner, 200), "ownBalance", "880");

        setRate(START.plusDays(1), "0.4");
        edit.put("date", START.plusDays(1).toString());
        json("PUT", path, edit, owner, 200);
        money(credit(destination), "amount", "300");
        money(credit(destination), "originalAmount", "120");
        money(credit(destination), "conversionRate", "2.5");
        money(json("GET", "/accounts/" + destination, null, owner, 200), "ownBalance", "300");
        json("DELETE", "/transactions/" + debit.path("id").asLong(), null, owner, 204);
        money(json("GET", "/accounts/" + source, null, owner, 200), "ownBalance", "1000");
        money(json("GET", "/accounts/" + destination, null, owner, 200), "ownBalance", "0");
    }

    @Test
    void convertedTransferInputsSurviveEditingAndInvalidMetadataIsRejected() throws Exception {
        setRate(START, "0.8");
        setRate(LocalDate.now(), "0.8");
        long source = account(owner);
        long destination = dollarAccount();
        JsonNode debit =
                json(
                        "POST",
                        "/transactions/transfer",
                        data(
                                "accountId",
                                source,
                                "toAccountId",
                                destination,
                                "type",
                                "TRANSFER",
                                "amount",
                                "80",
                                "currency",
                                "EUR",
                                "date",
                                START.toString(),
                                "originalAmount",
                                "100",
                                "originalCurrency",
                                "USD",
                                "conversionRate",
                                "0.8"),
                        owner,
                        201);
        money(credit(destination), "amount", "100");
        Map<String, Object> edit =
                data(
                        "fromAccountId",
                        source,
                        "toAccountId",
                        destination,
                        "amount",
                        "88",
                        "currency",
                        "EUR",
                        "date",
                        START.toString(),
                        "originalAmount",
                        "110",
                        "originalCurrency",
                        "USD",
                        "conversionRate",
                        "0.8");
        String path = "/transactions/transfers/" + debit.path("transferId").asText();
        JsonNode updated = json("PUT", path, edit, owner, 200);
        money(updated, "originalAmount", "110");
        money(credit(destination), "amount", "110");
        money(credit(destination), "originalAmount", "110");
        money(credit(destination), "conversionRate", "1");
        edit.put("originalAmount", "999");
        json("PUT", path, edit, owner, 400);
        money(credit(destination), "amount", "110");
        money(json("GET", "/accounts/" + source, null, owner, 200), "ownBalance", "912");
        edit.remove("conversionRate");
        json("PUT", path, edit, owner, 400);
    }

    private JsonNode credit(long accountId) throws Exception {
        JsonNode page =
                json("GET", "/transactions/search?accountId=" + accountId, null, owner, 200);
        assertThat(page.path("content")).hasSize(1);
        return page.path("content").get(0);
    }

    private long dollarAccount() throws Exception {
        return json(
                        "POST",
                        "/accounts",
                        data(
                                "name",
                                "Dollars",
                                "type",
                                "CHECKING",
                                "currency",
                                "USD",
                                "initialBalance",
                                "0",
                                "openingDate",
                                START.toString()),
                        owner,
                        201)
                .path("id")
                .asLong();
    }

    private void setRate(LocalDate date, String value) {
        if (!previousRates.containsKey(date)) {
            previousRates.put(
                    date,
                    rates.findByBaseCurrencyAndTargetCurrencyAndRateDate("USD", "EUR", date)
                            .map(ExchangeRate::getRate)
                            .orElse(null));
        }
        ExchangeRate rate =
                rates.findByBaseCurrencyAndTargetCurrencyAndRateDate("USD", "EUR", date)
                        .orElse(
                                ExchangeRate.builder()
                                        .baseCurrency("USD")
                                        .targetCurrency("EUR")
                                        .rateDate(date)
                                        .source("transfer-regression")
                                        .build());
        rate.setRate(new BigDecimal(value));
        rates.saveAndFlush(rate);
        caches.getCache("exchangeRates").clear();
    }
}
