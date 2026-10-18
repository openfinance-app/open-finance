package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Authenticated persistence and API parity, not just tests of an unused formula. */
class CalendarCompoundingIntegrationTest extends AuditApiTestSupport {
    @Test
    void previewAcceptsTheAccountPrecisionLimitAndDoesNotPersistAnything() throws Exception {
        Map<String, Object> request =
                data(
                        "balance",
                        "99999999999999999999999999.123456789012345678",
                        "rate",
                        "4",
                        "taxRate",
                        "0",
                        "interestPeriod",
                        "ANNUAL",
                        "openingDate",
                        LocalDate.now().withDayOfYear(1).toString(),
                        "currency",
                        "EUR");
        json("POST", "/accounts/interest-preview", request, owner, 200);
        json("POST", "/accounts/interest-preview", request, null, 403);
        request.put("balance", "100000000000000000000000000");
        json("POST", "/accounts/interest-preview", request, owner, 400);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM accounts WHERE user_id = ?",
                                Integer.class,
                                owner.id()))
                .isZero();
    }

    @Test
    void quarterlyProjectionUsesDatedRatesMovementsAndReplacesPostedInterest() throws Exception {
        int year = LocalDate.now().getYear();
        LocalDate january = LocalDate.of(year, 1, 1);
        long account =
                json(
                                "POST",
                                "/accounts",
                                data(
                                        "name",
                                        "Quarterly cash",
                                        "type",
                                        "SAVINGS",
                                        "currency",
                                        "EUR",
                                        "initialBalance",
                                        "1000",
                                        "openingDate",
                                        january.toString(),
                                        "isInterestEnabled",
                                        true,
                                        "interestPeriod",
                                        "QUARTERLY"),
                                owner,
                                201)
                        .path("id")
                        .asLong();
        String rates = "/accounts/" + account + "/interest-variations";
        json(
                "POST",
                rates,
                data(
                        "rate",
                        "4",
                        "taxRate",
                        "0",
                        "validFrom",
                        january.toString(),
                        "dayCountBasis",
                        "THIRTY_360"),
                owner,
                201);
        assertProjection(account, "40.60");

        // First quarter's €10 is actually posted on April 1. It replaces the estimated €10;
        // a separate €1,000 deposit on the SAME day remains new interest-bearing capital.
        long credit = movement(account, "INCOME", "10", january.plusMonths(3), "INTEREST");
        assertProjection(account, "40.60");
        json(
                "PUT",
                "/transactions/" + credit,
                data(
                        "accountId",
                        account,
                        "type",
                        "INCOME",
                        "amount",
                        "10",
                        "currency",
                        "EUR",
                        "date",
                        january.plusMonths(3).toString(),
                        "movementType",
                        null),
                owner,
                200);
        assertProjection(account, "40.91"); // An ordinary deposit must remain additional capital.
        json(
                "PUT",
                "/transactions/" + credit,
                data(
                        "accountId",
                        account,
                        "type",
                        "INCOME",
                        "amount",
                        "10",
                        "currency",
                        "EUR",
                        "date",
                        january.plusMonths(3).toString(),
                        "movementType",
                        "INTEREST"),
                owner,
                200);
        assertProjection(account, "40.60");
        json(
                "POST",
                "/transactions",
                data(
                        "accountId",
                        account,
                        "type",
                        "EXPENSE",
                        "amount",
                        "10",
                        "currency",
                        "EUR",
                        "date",
                        january.toString(),
                        "movementType",
                        "INTEREST"),
                owner,
                400);
        movement(account, "INCOME", "1000", january.plusMonths(3), null);
        movement(account, "EXPENSE", "500", january.plusMonths(6), null);
        // Q1=10; Q2=2010*.01=20.10; Q3=1530.10*.01=15.301; Q4=1545.401*.01=15.45401.
        assertProjection(account, "60.86");
        long changedRate =
                json(
                                "POST",
                                rates,
                                data(
                                        "rate",
                                        "8",
                                        "taxRate",
                                        "0",
                                        "validFrom",
                                        january.plusMonths(6).toString(),
                                        "dayCountBasis",
                                        "THIRTY_360"),
                                owner,
                                201)
                        .path("id")
                        .asLong();
        // Q3=30.602; Q4=1560.702*.02=31.21404.
        assertProjection(account, "91.92");
        json("DELETE", rates + "/" + changedRate, null, owner, 204);
        assertProjection(account, "60.86");
        json("DELETE", "/transactions/" + credit, null, owner, 204);
        assertProjection(account, "60.86");
        money(json("GET", "/accounts/" + account, null, owner, 200), "ownBalance", "1500");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM transactions WHERE account_id = ? AND is_deleted = false",
                                Integer.class,
                                account))
                .isEqualTo(2);
    }

    @Test
    void formPreviewAndSavedAccountUseTheSameCalendarAndNetCompounding() throws Exception {
        LocalDate opening = LocalDate.now().withDayOfYear(1).plusMonths(6);
        JsonNode preview =
                json(
                        "POST",
                        "/accounts/interest-preview",
                        data(
                                "balance",
                                "1000",
                                "rate",
                                "4",
                                "taxRate",
                                "20",
                                "interestPeriod",
                                "QUARTERLY",
                                "openingDate",
                                opening.toString(),
                                "currency",
                                "EUR"),
                        owner,
                        200);
        // Jul–Sep and Oct–Dec both have 92 actual days: 1000 * ((1 + .04*.8*92/365)^2 - 1).
        money(preview, "netInterest", "16.20");
        long account =
                json(
                                "POST",
                                "/accounts",
                                data(
                                        "name",
                                        "Preview parity",
                                        "type",
                                        "SAVINGS",
                                        "currency",
                                        "EUR",
                                        "initialBalance",
                                        "1000",
                                        "openingDate",
                                        opening.toString(),
                                        "isInterestEnabled",
                                        true,
                                        "interestPeriod",
                                        "QUARTERLY",
                                        "interestRate",
                                        "4",
                                        "taxRate",
                                        "20"),
                                owner,
                                201)
                        .path("id")
                        .asLong();
        assertProjection(account, "16.20");
        money(json("GET", "/accounts/" + account, null, owner, 200), "ownBalance", "1000");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM transactions WHERE account_id = ?",
                                Integer.class,
                                account))
                .isZero();
        json(
                "POST",
                "/accounts/interest-preview",
                data(
                        "balance",
                        "1000",
                        "rate",
                        "4",
                        "taxRate",
                        "101",
                        "interestPeriod",
                        "QUARTERLY",
                        "openingDate",
                        opening.toString(),
                        "currency",
                        "EUR"),
                owner,
                400);
    }

    private long movement(
            long account, String type, String amount, LocalDate date, String movementType)
            throws Exception {
        return json(
                        "POST",
                        "/transactions",
                        data(
                                "accountId",
                                account,
                                "type",
                                type,
                                "amount",
                                amount,
                                "date",
                                date.toString(),
                                "currency",
                                "EUR",
                                "movementType",
                                movementType),
                        owner,
                        201)
                .path("id")
                .asLong();
    }

    private void assertProjection(long account, String expected) throws Exception {
        money(
                json("GET", "/accounts/" + account + "/interest-estimate", null, owner, 200),
                "estimate",
                expected);
        money(
                json("GET", "/dashboard/estimated-interest?period=YTD", null, owner, 200),
                "totalProjected",
                expected);
    }
}
