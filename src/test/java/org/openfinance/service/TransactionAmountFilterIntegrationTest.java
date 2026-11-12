package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Transaction amount filters compare the displayed base-currency value")
class TransactionAmountFilterIntegrationTest extends AuditApiTestSupport {
    private long euros;
    private long francs;

    @BeforeEach
    void createAccounts() throws Exception {
        jdbc.update("UPDATE users SET base_currency = 'EUR' WHERE id = ?", owner.id());
        euros = account(owner);
        francs =
                json(
                                "POST",
                                "/accounts",
                                data(
                                        "name", "CFA account",
                                        "type", "CHECKING",
                                        "currency", "XOF",
                                        "initialBalance", 0,
                                        "openingDate", START.toString()),
                                owner,
                                201)
                        .path("id")
                        .asLong();
    }

    @Test
    void minimumAppliesBeforePaginationAndSummary() throws Exception {
        List<Long> expected = mixedCurrencies();

        JsonNode result = search("amountMin=2500");
        assertThat(ids(result.path("content"))).containsExactlyInAnyOrderElementsOf(expected);
        JsonNode page = search("amountMin=2500&size=1");
        assertThat(page.path("totalElements").asInt()).isEqualTo(3);
        assertThat(page.path("content").size()).isEqualTo(1);
        JsonNode summary = summary("amountMin=2500");
        assertThat(summary.path("transactionCount").asInt()).isEqualTo(3);
        money(summary, "incomeAmount", "3000");
        money(summary, "expenseAmount", "5000");
        money(summary, "transferNetAmount", "0");
        money(summary, "netAmount", "-2000");
    }

    @Test
    void maximumAndExactBoundsUseBookedAmountsAndKeepBothTransferLegs() throws Exception {
        mixedCurrencies();
        Map<String, Object> booked = request("EXPENSE", francs, "XOF", "40000");
        booked.put("originalAmount", new BigDecimal("500"));
        booked.put("originalCurrency", "EUR");
        booked.put("conversionRate", new BigDecimal("80"));
        JsonNode recorded = json("POST", "/transactions", booked, owner, 201);
        money(recorded, "amountInBaseCurrency", "500");

        assertThat(search("amountMax=500").path("totalElements").asInt()).isEqualTo(4);
        JsonNode exact = search("amountExact=500");
        assertThat(exact.path("totalElements").asInt()).isEqualTo(3);
        exact.path("content").forEach(row -> money(row, "amountInBaseCurrency", "500"));
        JsonNode summary = summary("amountMin=500&amountMax=500");
        money(summary, "expenseAmount", "500");
        money(summary, "transferNetAmount", "0");
        money(summary, "netAmount", "-500");
    }

    @Test
    void advancedSearchUsesTheSameMinimumAndMaximum() throws Exception {
        List<Long> expected = mixedCurrencies();
        JsonNode minimum = advanced("2500", null);
        assertThat(ids(minimum.path("resultsByType").path("TRANSACTION")))
                .containsExactlyInAnyOrderElementsOf(expected);
        assertThat(advanced(null, "500").path("totalResults").asInt()).isEqualTo(3);
        assertThat(advanced("500", "500").path("totalResults").asInt()).isEqualTo(2);
    }

    @Test
    void minimumAlsoIncludesSmallNativeAmountsWorthMoreInTheUsersBaseCurrency() throws Exception {
        jdbc.update("UPDATE users SET base_currency = 'XOF' WHERE id = ?", owner.id());
        long euroIncome = create("INCOME", euros, "EUR", "500");
        long xofExpense = create("EXPENSE", francs, "XOF", "40000");

        assertThat(ids(search("amountMin=2500").path("content")))
                .containsExactlyInAnyOrder(euroIncome, xofExpense);
        assertThat(ids(advanced("2500", null).path("resultsByType").path("TRANSACTION")))
                .containsExactlyInAnyOrder(euroIncome, xofExpense);
        assertThat(search("amountMax=2500").path("totalElements").asInt()).isZero();
        JsonNode summary = summary("amountMin=2500");
        assertThat(summary.path("currency").asText()).isEqualTo("XOF");
        money(summary, "incomeAmount", "327978.5");
        money(summary, "expenseAmount", "40000");
    }

    @Test
    void nativeModeAndExactMatchAreSharedBySearchSummaryAndAdvancedSearch() throws Exception {
        long euro = create("INCOME", euros, "EUR", "500");
        long foreign = create("EXPENSE", francs, "XOF", "40000");
        assertThat(search("amountMin=2500").path("totalElements").asInt()).isZero();
        assertThat(ids(search("amountMin=2500&amountCurrencyMode=NATIVE").path("content")))
                .containsExactly(foreign);
        assertThat(ids(search("amountExact=40000&amountCurrencyMode=NATIVE").path("content")))
                .containsExactly(foreign);
        assertThat(ids(search("amountExact=500.00").path("content"))).containsExactly(euro);
        JsonNode nativeSummary = summary("amountExact=40000&amountCurrencyMode=NATIVE");
        assertThat(nativeSummary.path("transactionCount").asInt()).isEqualTo(1);
        assertThat(nativeSummary.path("currency").asText()).isEqualTo("EUR");
        assertThat(nativeSummary.path("expenseAmount").decimalValue())
                .isBetween(new BigDecimal("60"), new BigDecimal("61"));
        JsonNode advanced =
                json(
                        "POST",
                        "/search/advanced",
                        data(
                                "query",
                                "Amount filter",
                                "entityTypes",
                                List.of("TRANSACTION"),
                                "exactAmount",
                                40000,
                                "amountCurrencyMode",
                                "NATIVE"),
                        owner,
                        200);
        assertThat(ids(advanced.path("resultsByType").path("TRANSACTION")))
                .containsExactly(foreign);
        assertThat(ids(search("sort=amount,desc&size=1").path("content"))).containsExactly(euro);
        assertThat(ids(search("sort=amount,desc&size=1&amountCurrencyMode=NATIVE").path("content")))
                .containsExactly(foreign);
        assertThat(
                        ids(
                                search("sort=amount,desc&size=1&amountCurrencyMode=NATIVE&page=1")
                                        .path("content")))
                .containsExactly(euro);
    }

    private List<Long> mixedCurrencies() throws Exception {
        create("EXPENSE", francs, "XOF", "40000");
        Map<String, Object> transfer = request("TRANSFER", euros, "EUR", "500");
        transfer.put("toAccountId", francs);
        json("POST", "/transactions/transfer", transfer, owner, 201);
        return List.of(
                create("EXPENSE", euros, "EUR", "2500"),
                create("EXPENSE", francs, "XOF", "1639892.5"),
                create("INCOME", euros, "EUR", "3000"));
    }

    private long create(String type, long account, String currency, String amount)
            throws Exception {
        return json("POST", "/transactions", request(type, account, currency, amount), owner, 201)
                .path("id")
                .asLong();
    }

    private Map<String, Object> request(String type, long account, String currency, String amount) {
        return data(
                "type",
                type,
                "accountId",
                account,
                "amount",
                new BigDecimal(amount),
                "currency",
                currency,
                "description",
                "Amount filter regression",
                "date",
                LocalDate.now().minusDays(10).toString());
    }

    private JsonNode search(String query) throws Exception {
        return json("GET", "/transactions/search?" + query, null, owner, 200);
    }

    private JsonNode summary(String query) throws Exception {
        return json("GET", "/transactions/summary?" + query, null, owner, 200);
    }

    private JsonNode advanced(String minimum, String maximum) throws Exception {
        return json(
                "POST",
                "/search/advanced",
                data(
                        "query",
                        "Amount filter regression",
                        "entityTypes",
                        List.of("TRANSACTION"),
                        "minAmount",
                        minimum == null ? null : new BigDecimal(minimum),
                        "maxAmount",
                        maximum == null ? null : new BigDecimal(maximum)),
                owner,
                200);
    }

    private List<Long> ids(JsonNode rows) {
        List<Long> result = new ArrayList<>();
        rows.forEach(row -> result.add(row.path("id").asLong()));
        return result;
    }
}
