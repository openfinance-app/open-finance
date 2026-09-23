package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Cash flow history API")
class CashFlowHistoryIntegrationTest extends AuditApiTestSupport {
    @BeforeEach
    void useEuros() {
        jdbc.update("UPDATE users SET base_currency = 'EUR' WHERE id = ?", owner.id());
    }

    @Test
    @DisplayName("Daily, monthly and yearly totals reconcile across leap days and year boundaries")
    void totalsReconcileAndStayScopedToOwner() throws Exception {
        long cash = historicalAccount(owner);
        long savings = historicalAccount(owner);
        movement(owner, cash, "INCOME", "1000.10", "2024-01-01");
        movement(owner, cash, "INCOME", "0.20", "2024-01-31");
        movement(owner, cash, "EXPENSE", "125.25", "2024-02-29");
        movement(owner, savings, "EXPENSE", "0.10", "2024-02-01");
        movement(owner, cash, "EXPENSE", "25.00", "2024-12-31");
        movement(owner, cash, "INCOME", "2000.00", "2025-01-01");
        JsonNode removed = movement(owner, cash, "EXPENSE", "999.99", "2024-02-29");
        json("DELETE", "/transactions/" + removed.path("id").asLong(), null, owner, 204);
        json(
                "POST",
                "/transactions/transfer",
                data(
                        "accountId",
                        cash,
                        "toAccountId",
                        savings,
                        "type",
                        "TRANSFER",
                        "amount",
                        400,
                        "currency",
                        "EUR",
                        "date",
                        "2024-02-29"),
                owner,
                201);
        Auth other = register();
        movement(other, historicalAccount(other), "INCOME", "9000", "2024-02-29");

        JsonNode daily =
                json(
                        "GET",
                        "/dashboard/cashflow-history?granularity=DAY&year=2024&month=2",
                        null,
                        owner,
                        200);
        assertThat(daily.size()).isEqualTo(29);
        assertThat(daily.get(28).path("date").asText()).isEqualTo("2024-02-29");
        assertMoney(daily.get(28), "expense", "125.25");
        assertMoney(daily.get(28), "income", "0");
        JsonNode legacy =
                json("GET", "/dashboard/daily-cashflow?year=2024&month=2", null, owner, 200);
        assertThat(daily).isEqualTo(legacy);

        JsonNode monthly =
                json(
                        "GET",
                        "/dashboard/cashflow-history?granularity=MONTH&year=2024",
                        null,
                        owner,
                        200);
        assertThat(monthly.size()).isEqualTo(12);
        assertMoney(monthly.get(0), "income", "1000.30");
        assertMoney(monthly.get(1), "expense", "125.35");
        assertMoney(monthly.get(1), "income", "0");
        assertMoney(monthly.get(2), "expense", "0");
        assertMoney(monthly.get(11), "expense", "25.00");

        JsonNode yearly =
                json(
                        "GET",
                        "/dashboard/cashflow-history?granularity=YEAR&year=2025",
                        null,
                        owner,
                        200);
        assertThat(yearly.size()).isEqualTo(10);
        assertThat(yearly.get(0).path("date").asText()).isEqualTo("2016-01-01");
        assertMoney(yearly.get(0), "income", "0");
        assertMoney(yearly.get(8), "income", "1000.30");
        assertMoney(yearly.get(8), "expense", "150.35");
        assertMoney(yearly.get(9), "income", "2000.00");
        assertMoney(yearly.get(9), "expense", "0");
    }

    @Test
    @DisplayName("Empty calendars include every month and default to daily granularity")
    void emptyPeriodsAndDefault() throws Exception {
        JsonNode monthly =
                json(
                        "GET",
                        "/dashboard/cashflow-history?granularity=MONTH&year=2023",
                        null,
                        owner,
                        200);
        assertThat(monthly.size()).isEqualTo(12);
        for (JsonNode period : monthly) {
            assertMoney(period, "income", "0");
            assertMoney(period, "expense", "0");
        }
        JsonNode daily =
                json("GET", "/dashboard/cashflow-history?year=2023&month=2", null, owner, 200);
        assertThat(daily.size()).isEqualTo(28);
    }

    @Test
    @DisplayName("Authentication and invalid period parameters are enforced")
    void rejectsInvalidRequests() throws Exception {
        json("GET", "/dashboard/cashflow-history", null, null, 403);
        for (String query :
                new String[] {
                    "granularity=WEEK",
                    "year=0",
                    "year=10000",
                    "month=0",
                    "month=13",
                    "granularity=YEAR&year=9"
                }) {
            json("GET", "/dashboard/cashflow-history?" + query, null, owner, 400);
        }
    }

    private long historicalAccount(Auth auth) throws Exception {
        return json(
                        "POST",
                        "/accounts",
                        data(
                                "name",
                                "History cash",
                                "type",
                                "CHECKING",
                                "currency",
                                "EUR",
                                "initialBalance",
                                10000,
                                "openingDate",
                                "2010-01-01"),
                        auth,
                        201)
                .path("id")
                .asLong();
    }

    private JsonNode movement(Auth auth, long account, String type, String amount, String date)
            throws Exception {
        Map<String, Object> payload =
                data(
                        "accountId",
                        account,
                        "type",
                        type,
                        "amount",
                        new BigDecimal(amount),
                        "currency",
                        "EUR",
                        "date",
                        date);
        return json("POST", "/transactions", payload, auth, 201);
    }

    private void assertMoney(JsonNode row, String field, String expected) {
        assertThat(new BigDecimal(row.path(field).asText())).isEqualByComparingTo(expected);
    }
}
