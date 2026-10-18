package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Proves the three fixed interest scopes and refresh after dated rate mutations. */
class AccountInterestSummaryIntegrationTest extends AuditApiTestSupport {
    @Test
    void summariesUseCalendarYearYearToDateAndLifetimeAndRecalculateRates() throws Exception {
        LocalDate today = LocalDate.now();
        LocalDate opening = today.withDayOfYear(1).minusMonths(1);
        long account =
                json(
                                "POST",
                                "/accounts",
                                data(
                                        "name", "Dated savings",
                                        "type", "SAVINGS",
                                        "currency", "EUR",
                                        "initialBalance", "1000",
                                        "openingDate", opening.toString(),
                                        "isInterestEnabled", true,
                                        "interestPeriod", "ANNUAL"),
                                owner,
                                201)
                        .path("id")
                        .asLong();
        String rates = "/accounts/" + account + "/interest-variations";
        json("POST", rates, rate("36.5", opening), owner, 201);
        json("POST", rates, rate("18.25", today.withDayOfYear(1)), owner, 201);
        json(
                "POST",
                "/transactions",
                data(
                        "accountId",
                        account,
                        "amount",
                        "1000",
                        "currency",
                        "EUR",
                        "type",
                        "INCOME",
                        "date",
                        today.toString()),
                owner,
                201);

        // After 20% tax: €0.80/day last December, €0.40/day this year,
        // then €0.80/day on the doubled balance from today onward.
        BigDecimal elapsed =
                new BigDecimal("0.4").multiply(BigDecimal.valueOf(today.getDayOfYear() - 1));
        BigDecimal yearToDate = elapsed.add(new BigDecimal("0.8"));
        BigDecimal lifetime = yearToDate.add(new BigDecimal("24.8"));
        long remainingDays = today.lengthOfYear() - today.getDayOfYear();
        BigDecimal projected =
                yearToDate.add(new BigDecimal("0.8").multiply(BigDecimal.valueOf(remainingDays)));
        assertSummary(account, projected, yearToDate, lifetime);

        Map<String, Object> scheduled = rate("36.5", today.plusDays(1));
        long variation = json("POST", rates, scheduled, owner, 201).path("id").asLong();
        assertSummary(
                account,
                projected.add(new BigDecimal("0.8").multiply(BigDecimal.valueOf(remainingDays))),
                yearToDate,
                lifetime);
        scheduled.put("rate", "54.75");
        json("PUT", rates + "/" + variation, scheduled, owner, 200);
        assertSummary(
                account,
                projected.add(new BigDecimal("1.6").multiply(BigDecimal.valueOf(remainingDays))),
                yearToDate,
                lifetime);
        json("DELETE", rates + "/" + variation, null, owner, 204);
        assertSummary(account, projected, yearToDate, lifetime);

        money(json("GET", "/accounts/" + account, null, owner, 200), "ownBalance", "2000");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM transactions WHERE account_id = ?",
                                Integer.class,
                                account))
                .isEqualTo(1);
        json("GET", "/accounts/" + account + "/interest-estimate", null, register(), 404);
    }

    private Map<String, Object> rate(String value, LocalDate from) {
        return data("rate", value, "taxRate", "20", "validFrom", from.toString());
    }

    private void assertSummary(
            long account, BigDecimal projected, BigDecimal yearToDate, BigDecimal lifetime)
            throws Exception {
        JsonNode summary =
                json("GET", "/accounts/" + account + "/interest-estimate", null, owner, 200);
        money(summary, "estimate", projected.toPlainString());
        money(summary, "yearToDate", yearToDate.toPlainString());
        money(summary, "historicalAccumulated", lifetime.toPlainString());
        JsonNode dashboard =
                json("GET", "/dashboard/estimated-interest?period=YTD", null, owner, 200);
        money(dashboard, "totalProjected", projected.toPlainString());
        money(dashboard, "totalEarned", yearToDate.toPlainString());
        money(dashboard.path("accounts").get(0), "projectedInterest", projected.toPlainString());
        JsonNode oneDay =
                json(
                        "GET",
                        "/dashboard/estimated-interest?startDate="
                                + LocalDate.now()
                                + "&endDate="
                                + LocalDate.now(),
                        null,
                        owner,
                        200);
        money(oneDay, "totalEarned", "0.8");
        money(oneDay, "totalProjected", projected.toPlainString());
    }
}
