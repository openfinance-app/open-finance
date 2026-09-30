package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openfinance.entity.ExchangeRate;
import org.openfinance.repository.ExchangeRateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;

/** Round-three failures exercised through authenticated APIs and real persistence. */
class FinanceRoundThreeIntegrationTest extends AuditApiTestSupport {
    @Autowired ExchangeRateRepository rates;
    @Autowired CacheManager caches;

    @BeforeEach
    void baseCurrency() {
        jdbc.update("UPDATE users SET base_currency = 'EUR' WHERE id = ?", owner.id());
        caches.getCacheNames().forEach(name -> caches.getCache(name).clear());
    }

    private JsonNode debt(String balance, String rate, String payment) throws Exception {
        return json(
                "POST",
                "/liabilities",
                data(
                        "name",
                        "Loan",
                        "type",
                        "PERSONAL_LOAN",
                        "principal",
                        new BigDecimal(balance).signum() == 0 ? "1000" : balance,
                        "currentBalance",
                        balance,
                        "currency",
                        "EUR",
                        "startDate",
                        START.toString(),
                        "interestRate",
                        rate,
                        "minimumPayment",
                        payment),
                owner,
                201);
    }

    private Map<String, Object> holding(String type) {
        return data(
                "name",
                "Holding",
                "type",
                type,
                "quantity",
                1,
                "purchasePrice",
                9000,
                "currentPrice",
                9000,
                "currency",
                "EUR",
                "purchaseDate",
                START.toString());
    }

    @Test
    void genericDrawRejectsForeignTranchesOverdrawAndDuplicateWithoutPartialWrites()
            throws Exception {
        long account = account(owner);
        long left = debt("0", "0", "100").path("id").asLong();
        long right = debt("0", "0", "100").path("id").asLong();
        long tranche =
                json(
                                "POST",
                                "/liabilities/" + left + "/tranches",
                                data("plannedAmount", 1000, "currency", "EUR"),
                                owner,
                                201)
                        .path("id")
                        .asLong();
        Map<String, Object> draw = movement(account, 1000, LocalDate.now());
        draw.putAll(
                data(
                        "type",
                        "INCOME",
                        "movementType",
                        "DISBURSEMENT",
                        "liabilityId",
                        right,
                        "trancheId",
                        tranche));
        json("POST", "/transactions", draw, owner, 400);
        money(json("GET", "/accounts/" + account, null, owner, 200), "ownBalance", "1000");
        draw.putAll(data("liabilityId", left, "amount", 1001));
        json("POST", "/transactions", draw, owner, 409);
        money(json("GET", "/liabilities/" + left, null, owner, 200), "currentBalance", "0");
        json(
                "POST",
                "/liabilities/" + left + "/disburse",
                data(
                        "toAccountId",
                        account,
                        "amount",
                        1000,
                        "date",
                        LocalDate.now().toString(),
                        "trancheId",
                        tranche),
                owner,
                200);
        draw.put("amount", 1000);
        json("POST", "/transactions", draw, owner, 400);
        money(json("GET", "/accounts/" + account, null, owner, 200), "ownBalance", "2000");
        money(json("GET", "/liabilities/" + left, null, owner, 200), "currentBalance", "1000");
        money(
                json("GET", "/liabilities/" + left + "/tranches", null, owner, 200).get(0),
                "drawnAmount",
                "1000");
    }

    @Test
    void backdatedAccountCreationAndOpeningDateChangesRefreshExistingHistory() throws Exception {
        account(owner);
        String history =
                "/dashboard/networth-history?startDate=" + START + "&endDate=" + LocalDate.now();
        money(json("GET", history, null, owner, 200).get(0), "netWorth", "1000");
        long second = account(owner);
        JsonNode normal = json("GET", history, null, owner, 200);
        money(normal.get(0), "netWorth", "2000");
        assertThat(normal).isEqualTo(json("GET", history + "&recalculate=true", null, owner, 200));
        json(
                "PUT",
                "/accounts/" + second,
                data(
                        "name",
                        "Later opening",
                        "type",
                        "CHECKING",
                        "currency",
                        "EUR",
                        "initialBalance",
                        1000,
                        "openingDate",
                        START.plusMonths(1).toString()),
                owner,
                200);
        money(json("GET", history, null, owner, 200).get(0), "netWorth", "1000");
    }

    @Test
    void activationRoundTripKeepsCurrentCardsConsistentAndRetainsTheAsset() throws Exception {
        account(owner);
        JsonNode p = property(owner);
        long id = p.path("id").asLong();
        Map<String, Object> update =
                data(
                        "name",
                        "House",
                        "propertyType",
                        "RESIDENTIAL",
                        "address",
                        "Synthetic",
                        "purchasePrice",
                        1000,
                        "currentValue",
                        1000,
                        "currency",
                        "EUR",
                        "purchaseDate",
                        START.toString(),
                        "isActive",
                        false);
        assertThat(
                        json("PUT", "/real-estate/" + id, update, owner, 200)
                                .path("isActive")
                                .asBoolean())
                .isFalse();
        money(
                json("GET", "/dashboard/summary", null, owner, 200).path("netWorth"),
                "netWorth",
                "1000");
        BigDecimal allocation = BigDecimal.ZERO;
        for (JsonNode row : json("GET", "/dashboard/networth-allocation", null, owner, 200))
            allocation = allocation.add(row.path("value").decimalValue());
        assertThat(allocation).isEqualByComparingTo("1000");
        JsonNode asset = json("GET", "/assets/" + p.path("assetId").asLong(), null, owner, 200);
        money(asset, "totalValue", "1000");
        assertThat(asset.path("isActive").asBoolean()).isFalse();
        update.put("isActive", true);
        json("PUT", "/real-estate/" + id, update, owner, 200);
        money(
                json("GET", "/dashboard/summary", null, owner, 200).path("netWorth"),
                "netWorth",
                "2000");
        assertThat(
                        json("GET", "/assets/" + p.path("assetId").asLong(), null, owner, 200)
                                .path("isActive")
                                .asBoolean())
                .isTrue();
    }

    @Test
    void interestProjectionUsesOnlyCashEvenWhenInvestmentsAreLinked() throws Exception {
        long account =
                json(
                                "POST",
                                "/accounts",
                                data(
                                        "name",
                                        "Savings",
                                        "type",
                                        "SAVINGS",
                                        "currency",
                                        "EUR",
                                        "initialBalance",
                                        1000,
                                        "openingDate",
                                        START.toString(),
                                        "isInterestEnabled",
                                        true,
                                        "interestPeriod",
                                        "ANNUAL",
                                        "interestRate",
                                        5,
                                        "taxRate",
                                        0),
                                owner,
                                201)
                        .path("id")
                        .asLong();
        Map<String, Object> asset = holding("STOCK");
        asset.put("accountId", account);
        json("POST", "/assets", asset, owner, 201);
        money(json("GET", "/accounts/" + account, null, owner, 200), "balance", "10000");
        money(
                json(
                        "GET",
                        "/accounts/" + account + "/interest-estimate?period=1Y",
                        null,
                        owner,
                        200),
                "estimate",
                "50");
        money(
                json("GET", "/dashboard/estimated-interest?period=1Y", null, owner, 200),
                "totalProjected",
                "50");
    }

    @Test
    void scheduleUsesPreciseRatesAndIncludesInsuranceInEveryCashPayment() throws Exception {
        long loan = debt("300000", "4.1", "2000").path("id").asLong();
        JsonNode schedule = json("GET", "/liabilities/" + loan + "/amortization", null, owner, 200);
        money(schedule.get(0), "interestPortion", "1025");
        BigDecimal totalInterest = BigDecimal.ZERO;
        for (JsonNode row : schedule)
            totalInterest = totalInterest.add(row.path("interestPortion").decimalValue());
        assertThat(totalInterest).isEqualByComparingTo("121283.67");
        JsonNode insured =
                json(
                        "POST",
                        "/liabilities",
                        data(
                                "name",
                                "Insured",
                                "type",
                                "PERSONAL_LOAN",
                                "principal",
                                10000,
                                "currentBalance",
                                10000,
                                "currency",
                                "EUR",
                                "startDate",
                                START.toString(),
                                "interestRate",
                                12,
                                "minimumPayment",
                                1000,
                                "insurancePercentage",
                                "1.2"),
                        owner,
                        201);
        JsonNode payments =
                json(
                        "GET",
                        "/liabilities/" + insured.path("id").asLong() + "/amortization",
                        null,
                        owner,
                        200);
        money(payments.get(0), "paymentAmount", "1010");
        for (JsonNode row : payments) {
            money(row, "insurancePortion", "10");
            assertThat(row.path("paymentAmount").decimalValue())
                    .isEqualByComparingTo(
                            row.path("principalPortion")
                                    .decimalValue()
                                    .add(row.path("interestPortion").decimalValue())
                                    .add(new BigDecimal("10")));
        }
        money(payments.get(payments.size() - 1), "remainingBalance", "0");
    }

    @Test
    void incompatibleTypeChangeIsRejectedAndImprovementCanStillBeReversed() throws Exception {
        long account = account(owner);
        Map<String, Object> asset = holding("VEHICLE");
        long id = json("POST", "/assets", asset, owner, 201).path("id").asLong();
        Map<String, Object> movement = movement(account, 120, LocalDate.now());
        movement.putAll(data("assetId", id, "movementType", "CAPITAL_IMPROVEMENT"));
        long tx = json("POST", "/transactions", movement, owner, 201).path("id").asLong();
        asset.putAll(data("type", "OTHER", "currentPrice", 9120));
        json("PUT", "/assets/" + id, asset, owner, 409);
        assertThat(json("GET", "/assets/" + id, null, owner, 200).path("type").asText())
                .isEqualTo("VEHICLE");
        json("DELETE", "/transactions/" + tx, null, owner, 204);
        money(json("GET", "/accounts/" + account, null, owner, 200), "ownBalance", "1000");
        money(json("GET", "/assets/" + id, null, owner, 200), "totalValue", "9000");
        asset.put("currentPrice", 9000);
        json("PUT", "/assets/" + id, asset, owner, 200);
    }

    @Test
    void transactionReportingUsesBookedDatedFxAndAcceptsSupportedCryptoOriginalCurrency()
            throws Exception {
        long account = account(owner);
        setRate(START, "0.8");
        setRate(LocalDate.now(), "0.9");
        Map<String, Object> expense = movement(account, 100, START);
        expense.put("currency", "USD");
        JsonNode posted = json("POST", "/transactions", expense, owner, 201);
        money(posted, "accountAmount", "80");
        money(posted, "amountInBaseCurrency", "80");
        // Changing the rate catalog must not rewrite the already booked EUR leg.
        setRate(START, "0.7");
        money(
                json("GET", "/transactions/" + posted.path("id").asLong(), null, owner, 200),
                "amountInBaseCurrency",
                "80");
        expense.putAll(
                data(
                        "currency",
                        "EUR",
                        "amount",
                        9,
                        "originalCurrency",
                        "USDT",
                        "originalAmount",
                        10,
                        "conversionRate",
                        "0.9"));
        JsonNode crypto = json("POST", "/transactions", expense, owner, 201);
        assertThat(crypto.path("originalCurrency").asText()).isEqualTo("USDT");
        money(json("GET", "/accounts/" + account, null, owner, 200), "ownBalance", "911");
    }

    private void setRate(LocalDate date, String value) {
        ExchangeRate rate =
                rates.findByBaseCurrencyAndTargetCurrencyAndRateDate("USD", "EUR", date)
                        .orElse(
                                ExchangeRate.builder()
                                        .baseCurrency("USD")
                                        .targetCurrency("EUR")
                                        .rateDate(date)
                                        .source("round-three")
                                        .build());
        rate.setRate(new BigDecimal(value));
        rates.saveAndFlush(rate);
        caches.getCache("exchangeRates").clear();
    }

    @Test
    void twoYearAppreciationIsCompoundedRatherThanAveraged() throws Exception {
        JsonNode property =
                json(
                        "POST",
                        "/real-estate",
                        data(
                                "name",
                                "Investment",
                                "propertyType",
                                "RESIDENTIAL",
                                "address",
                                "Synthetic",
                                "purchasePrice",
                                100000,
                                "currentValue",
                                144000,
                                "purchaseDate",
                                LocalDate.now().minusYears(2).toString(),
                                "currency",
                                "EUR"),
                        owner,
                        201);
        money(
                json(
                        "GET",
                        "/real-estate/" + property.path("id").asLong() + "/roi",
                        null,
                        owner,
                        200),
                "annualizedReturn",
                "20");
    }
}
