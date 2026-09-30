package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openfinance.entity.ExchangeRate;
import org.openfinance.entity.ImportSession;
import org.openfinance.repository.ExchangeRateRepository;
import org.openfinance.security.EncryptionContext;
import org.openfinance.security.EncryptionKeyCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.mock.web.MockMultipartFile;

/** Reproductions from the second audit through authenticated APIs and encrypted persistence. */
class FinanceRoundTwoIntegrationTest extends AuditApiTestSupport {
    @Autowired ExchangeRateRepository rates;
    @Autowired CacheManager caches;
    @Autowired EncryptionKeyCache keys;
    @Autowired FileStorageService files;
    @Autowired ImportService imports;

    @BeforeEach
    void reportingCurrency() {
        jdbc.update("UPDATE users SET base_currency = 'EUR' WHERE id = ?", owner.id());
        for (String currency : new String[] {"USD", "BTC"}) {
            for (LocalDate date : new LocalDate[] {START.minusYears(1), LocalDate.now()}) {
                ExchangeRate rate =
                        rates.findByBaseCurrencyAndTargetCurrencyAndRateDate(currency, "EUR", date)
                                .orElse(
                                        ExchangeRate.builder()
                                                .baseCurrency(currency)
                                                .targetCurrency("EUR")
                                                .rateDate(date)
                                                .source("audit-round-two")
                                                .build());
                rate.setRate(new BigDecimal(currency.equals("USD") ? "0.9" : "50000"));
                rates.saveAndFlush(rate);
            }
        }
        caches.getCacheNames().forEach(name -> caches.getCache(name).clear());
    }

    private long cash(String currency, String amount, LocalDate opening) throws Exception {
        return json(
                        "POST",
                        "/accounts",
                        data(
                                "name",
                                "Audit cash",
                                "type",
                                "CHECKING",
                                "currency",
                                currency,
                                "initialBalance",
                                amount,
                                "openingDate",
                                opening.toString()),
                        owner,
                        201)
                .path("id")
                .asLong();
    }

    private Map<String, Object> holding() {
        return data(
                "name",
                "Car",
                "type",
                "VEHICLE",
                "quantity",
                1,
                "purchasePrice",
                1000,
                "currentPrice",
                1000,
                "currency",
                "EUR",
                "purchaseDate",
                START.toString(),
                "acquisitionType",
                "PURCHASE");
    }

    private Map<String, Object> debt(String principal, String balance) {
        return data(
                "name",
                "Audit loan",
                "type",
                "LOAN",
                "principal",
                principal,
                "currentBalance",
                balance,
                "currency",
                "EUR",
                "interestRate",
                0,
                "startDate",
                START.toString(),
                "endDate",
                START.plusYears(1).toString());
    }

    @Test
    void fractionalPrincipalSurvivesPostingEditingAndDeletion() throws Exception {
        long cash = cash("BTC", "0.1", START);
        Map<String, Object> debt = debt("0.1", "0.1");
        debt.put("currency", "BTC");
        long loan = json("POST", "/liabilities", debt, owner, 201).path("id").asLong();
        Map<String, Object> payment = movement(cash, 1, LocalDate.now());
        payment.putAll(
                data(
                        "amount",
                        "0.0049",
                        "principalAmount",
                        "0.0049",
                        "currency",
                        "BTC",
                        "liabilityId",
                        loan,
                        "movementType",
                        "REPAYMENT"));
        JsonNode posted = json("POST", "/transactions", payment, owner, 201);
        money(posted, "principalAmount", "0.0049");
        money(json("GET", "/liabilities/" + loan, null, owner, 200), "currentBalance", "0.0951");
        payment.putAll(data("amount", "0.00001234", "principalAmount", "0.00001234"));
        json("PUT", "/transactions/" + posted.path("id").asLong(), payment, owner, 200);
        money(json("GET", "/accounts/" + cash, null, owner, 200), "ownBalance", "0.09998766");
        money(
                json("GET", "/liabilities/" + loan, null, owner, 200),
                "currentBalance",
                "0.09998766");
        json("DELETE", "/transactions/" + posted.path("id").asLong(), null, owner, 204);
        money(json("GET", "/liabilities/" + loan, null, owner, 200), "currentBalance", "0.1");
        money(json("GET", "/accounts/" + cash, null, owner, 200), "ownBalance", "0.1");
    }

    @Test
    void smallCryptoLoanHasNonzeroCalculatedPaymentsAndAnExactPrincipalSchedule() throws Exception {
        Map<String, Object> loan = debt("0.000012", "0.000012");
        loan.put("currency", "BTC");
        JsonNode created = json("POST", "/liabilities", loan, owner, 201);
        money(created, "effectiveMonthlyPayment", "0.000001");
        JsonNode schedule =
                json(
                        "GET",
                        "/liabilities/" + created.path("id").asLong() + "/amortization",
                        null,
                        owner,
                        200);
        assertThat(schedule).hasSize(12);
        BigDecimal repaid = BigDecimal.ZERO;
        for (JsonNode payment : schedule)
            repaid = repaid.add(payment.path("principalPortion").decimalValue());
        assertThat(repaid).isEqualByComparingTo("0.000012");
    }

    @Test
    void partialFundingAndItsReversalPreservePropertyAndBackingAssetValues() throws Exception {
        JsonNode property = property(owner);
        long propertyId = property.path("id").asLong();
        Map<String, Object> debt = debt("600", "0");
        debt.putAll(data("type", "MORTGAGE", "realEstateId", propertyId));
        long loan = json("POST", "/liabilities", debt, owner, 201).path("id").asLong();
        for (int amount : new int[] {200, 400}) {
            json(
                    "POST",
                    "/liabilities/" + loan + "/disburse",
                    data(
                            "amount",
                            amount,
                            "date",
                            LocalDate.now().toString(),
                            "directRealEstateId",
                            propertyId),
                    owner,
                    200);
        }
        money(json("GET", "/real-estate/" + propertyId, null, owner, 200), "currentValue", "1000");
        money(
                json("GET", "/assets/" + property.path("assetId").asLong(), null, owner, 200),
                "totalValue",
                "1000");
        money(json("GET", "/liabilities/" + loan, null, owner, 200), "currentBalance", "600");
        JsonNode tranches = json("GET", "/liabilities/" + loan + "/tranches", null, owner, 200);
        json(
                "POST",
                "/tranches/" + tranches.get(1).path("id").asLong() + "/reverse",
                data("date", LocalDate.now().toString()),
                owner,
                200);
        money(json("GET", "/real-estate/" + propertyId, null, owner, 200), "currentValue", "1000");
    }

    @Test
    void reversalOfAnOlderImprovementPreservesLaterIndependentEstimates() throws Exception {
        long cash = account(owner);
        long property = property(owner).path("id").asLong();
        Map<String, Object> expense = movement(cash, 100, START.plusMonths(1));
        expense.putAll(data("realEstateId", property, "movementType", "CAPITAL_IMPROVEMENT"));
        long transaction = json("POST", "/transactions", expense, owner, 201).path("id").asLong();
        json("PUT", "/real-estate/" + property + "/value", 1200, owner, 200);
        json("DELETE", "/transactions/" + transaction, null, owner, 204);
        money(json("GET", "/real-estate/" + property, null, owner, 200), "currentValue", "1200");
        Map<String, Object> asset = holding();
        long id = json("POST", "/assets", asset, owner, 201).path("id").asLong();
        expense.remove("realEstateId");
        expense.put("assetId", id);
        transaction = json("POST", "/transactions", expense, owner, 201).path("id").asLong();
        asset.put("currentPrice", 700);
        json("PUT", "/assets/" + id, asset, owner, 200);
        json("DELETE", "/transactions/" + transaction, null, owner, 204);
        money(json("GET", "/assets/" + id, null, owner, 200), "totalValue", "700");
    }

    @Test
    void sameDayValuationDoesNotLoseValueWhenAnEarlierPostingIsReversed() throws Exception {
        long cash = account(owner), property = property(owner).path("id").asLong();
        Map<String, Object> expense = movement(cash, 100, LocalDate.now());
        expense.putAll(data("realEstateId", property, "movementType", "CAPITAL_IMPROVEMENT"));
        long transaction = json("POST", "/transactions", expense, owner, 201).path("id").asLong();
        json("PUT", "/real-estate/" + property + "/value", 1500, owner, 200);
        json("DELETE", "/transactions/" + transaction, null, owner, 204);
        money(json("GET", "/real-estate/" + property, null, owner, 200), "currentValue", "1500");
        money(history(LocalDate.now(), LocalDate.now()).get(0), "netWorth", "2500");
    }

    @Test
    void legacyAssetReversalRequiresAConfirmedValuationAndRollsBackAtomically() throws Exception {
        long cash = account(owner);
        Map<String, Object> asset = holding();
        long id = json("POST", "/assets", asset, owner, 201).path("id").asLong();
        Map<String, Object> expense = movement(cash, 100, START.plusMonths(1));
        expense.putAll(data("assetId", id, "movementType", "CAPITAL_IMPROVEMENT"));
        long transaction = json("POST", "/transactions", expense, owner, 201).path("id").asLong();
        jdbc.update("UPDATE assets SET valuation_recorded_at = NULL WHERE id = ?", id);
        JsonNode rejected = json("DELETE", "/transactions/" + transaction, null, owner, 409);
        assertThat(rejected.path("message").asText()).contains("valuation");
        money(json("GET", "/accounts/" + cash, null, owner, 200), "ownBalance", "900");
        money(json("GET", "/assets/" + id, null, owner, 200), "totalValue", "1100");
        // Confirming the same numeric value must still establish the missing boundary.
        asset.put("currentPrice", 1100);
        json("PUT", "/assets/" + id, asset, owner, 200);
        json("DELETE", "/transactions/" + transaction, null, owner, 204);
        money(json("GET", "/accounts/" + cash, null, owner, 200), "ownBalance", "1000");
        money(json("GET", "/assets/" + id, null, owner, 200), "totalValue", "1100");
    }

    @Test
    void capitalizedExpensesAreIncludedInCostAndDoNotProduceProfit() throws Exception {
        long cash = account(owner), property = property(owner).path("id").asLong();
        Map<String, Object> expense = movement(cash, 100, START.plusMonths(1));
        expense.putAll(data("realEstateId", property, "movementType", "CAPITAL_IMPROVEMENT"));
        json("POST", "/transactions", expense, owner, 201);
        JsonNode roi = json("GET", "/real-estate/" + property + "/roi", null, owner, 200);
        money(roi, "costBasis", "1100");
        money(roi, "totalROI", "0");
        money(json("GET", "/real-estate/" + property, null, owner, 200), "appreciation", "0");
        long asset = json("POST", "/assets", holding(), owner, 201).path("id").asLong();
        expense.remove("realEstateId");
        expense.put("assetId", asset);
        json("POST", "/transactions", expense, owner, 201);
        JsonNode result = json("GET", "/assets/" + asset, null, owner, 200);
        money(result, "totalCost", "1100");
        money(result, "unrealizedGain", "0");
        JsonNode performance =
                json("GET", "/dashboard/portfolio-performance?period=30", null, owner, 200);
        money(performance.get(1), "currentValue", "0");
        money(performance.get(2), "currentValue", "2200");
    }

    @Test
    void accountOpeningCannotBeMovedPastExistingActivity() throws Exception {
        long cash = account(owner);
        json("POST", "/transactions", movement(cash, 100, START.plusMonths(1)), owner, 201);
        json(
                "PUT",
                "/accounts/" + cash,
                data(
                        "name",
                        "Cash",
                        "type",
                        "CHECKING",
                        "currency",
                        "EUR",
                        "initialBalance",
                        900,
                        "openingDate",
                        START.plusMonths(2).toString()),
                owner,
                400);
        JsonNode history =
                json("GET", "/accounts/" + cash + "/balance-history?period=ALL", null, owner, 200);
        money(history.get(history.size() - 1), "balance", "900");
    }

    @Test
    void annualFiguresNeedNoPriorReadsAndHonorCurrenciesAndAccountLifetime() throws Exception {
        int year = LocalDate.now().getYear();
        long euro = cash("EUR", "1100", LocalDate.of(year - 1, 1, 1));
        long dollar = cash("USD", "1100", LocalDate.of(year, 1, 1));
        JsonNode annual = json("GET", "/dashboard/yearly-balance", null, owner, 200);
        assertThat(annual.path("years")).hasSize(2);
        money(annual.path("netWorth").get(0), "amount", "1100");
        money(annual.path("netWorth").get(1), "amount", "2090");
        for (JsonNode account : annual.path("accounts")) {
            if (account.path("id").asLong() == dollar) {
                money(account.path("data").get(0), "amount", "0");
                money(account.path("data").get(1), "amount", "990");
            }
        }
        money(annual.path("institutions").get(0).path("data").get(1), "amount", "2090");
        json("POST", "/accounts/" + euro + "/close", null, owner, 204);
        JsonNode closed = json("GET", "/dashboard/yearly-balance", null, owner, 200);
        assertThat(closed.path("accounts")).hasSize(2);
        money(closed.path("netWorth").get(0), "amount", "1100");
        money(closed.path("netWorth").get(1), "amount", "990");
    }

    @Test
    void recalculationUsesCurrentQuotesForToday() throws Exception {
        account(owner);
        Map<String, Object> asset = holding();
        asset.putAll(data("type", "STOCK", "currentPrice", 1200));
        json("POST", "/assets", asset, owner, 201);
        JsonNode observations = history(START, LocalDate.now());
        money(observations.get(observations.size() - 1), "netWorth", "2200");
    }

    @Test
    void explicitNullDetachesAnAssetWhileOmissionPreservesTheLink() throws Exception {
        long cash = account(owner);
        Map<String, Object> asset = holding();
        asset.put("accountId", cash);
        long id = json("POST", "/assets", asset, owner, 201).path("id").asLong();
        asset.remove("accountId");
        assertThat(json("PUT", "/assets/" + id, asset, owner, 200).path("accountId").asLong())
                .isEqualTo(cash);
        asset.put("accountId", null);
        assertThat(json("PUT", "/assets/" + id, asset, owner, 200).path("accountId").isNull())
                .isTrue();
        money(json("GET", "/accounts/" + cash, null, owner, 200), "balance", "1000");
    }

    @Test
    void debtServiceExcludesSettledLoansAndDerivesMissingPayments() throws Exception {
        Map<String, Object> paid = debt("12000", "0");
        paid.put("minimumPayment", 1000);
        JsonNode result = json("POST", "/liabilities", paid, owner, 201);
        money(result, "effectiveMonthlyPayment", "0");
        money(
                json("GET", "/dashboard/borrowing-capacity?period=30", null, owner, 200),
                "monthlyDebtPayments",
                "0");
        result = json("POST", "/liabilities", debt("12000", "12000"), owner, 201);
        money(result, "effectiveMonthlyPayment", "1000");
        money(
                json("GET", "/dashboard/borrowing-capacity?period=30", null, owner, 200),
                "monthlyDebtPayments",
                "1000");
    }

    @Test
    void incompleteLoanTermsAreReportedInsteadOfBeingCountedAsZero() throws Exception {
        Map<String, Object> unknown = debt("1000", "1000");
        unknown.remove("endDate");
        JsonNode result = json("POST", "/liabilities", unknown, owner, 201);
        assertThat(result.path("effectiveMonthlyPayment").isNull()).isTrue();
        JsonNode capacity =
                json("GET", "/dashboard/borrowing-capacity?period=30", null, owner, 200);
        assertThat(capacity.path("debtPaymentsComplete").asBoolean()).isFalse();
        assertThat(capacity.path("financialHealthStatus").asText()).isEqualTo("INSUFFICIENT_DATA");
    }

    @Test
    void closingAnAccountPausesRecurringPostingsAndExecutionRechecksTheAccount() throws Exception {
        long cash = account(owner);
        JsonNode template =
                json(
                        "POST",
                        "/recurring-transactions",
                        data(
                                "accountId",
                                cash,
                                "type",
                                "EXPENSE",
                                "amount",
                                50,
                                "currency",
                                "EUR",
                                "description",
                                "Insurance",
                                "frequency",
                                "MONTHLY",
                                "nextOccurrence",
                                LocalDate.now().toString()),
                        owner,
                        201);
        long id = template.path("id").asLong();
        json("POST", "/accounts/" + cash + "/close", null, owner, 204);
        assertThat(
                        json("GET", "/recurring-transactions/" + id, null, owner, 200)
                                .path("isActive")
                                .asBoolean())
                .isFalse();
        // A legacy active template must also be stopped at the posting boundary.
        jdbc.update("UPDATE recurring_transactions SET is_active = ? WHERE id = ?", true, id);
        json("POST", "/recurring-transactions/process", null, owner, 200);
        money(json("GET", "/accounts/" + cash, null, owner, 200), "ownBalance", "1000");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM transactions WHERE account_id = ?",
                                Integer.class,
                                cash))
                .isZero();
    }

    @Test
    void repaymentPreviewUsesPrincipalAtTheRequestedDate() throws Exception {
        long cash = cash("EUR", "20000", START);
        Map<String, Object> debt = debt("12000", "12000");
        debt.put("interestRate", 12);
        long loan = json("POST", "/liabilities", debt, owner, 201).path("id").asLong();
        Map<String, Object> expense = movement(cash, 1000, LocalDate.now());
        expense.putAll(
                data("liabilityId", loan, "principalAmount", 1000, "movementType", "REPAYMENT"));
        json("POST", "/transactions", expense, owner, 201);
        JsonNode preview =
                json(
                        "GET",
                        "/liabilities/"
                                + loan
                                + "/repayment-preview?total=1120&date="
                                + LocalDate.now().minusMonths(1),
                        null,
                        owner,
                        200);
        money(preview, "interest", "120");
        money(preview, "principal", "1000");
    }

    @Test
    void overdraftsAreSeparateFromPositiveCashInAllocation() throws Exception {
        account(owner);
        cash("EUR", "-100", START);
        JsonNode allocation = json("GET", "/dashboard/networth-allocation", null, owner, 200);
        assertThat(allocation).hasSize(2);
        for (JsonNode entry : allocation) {
            money(entry, "value", entry.path("isLiability").asBoolean() ? "-100" : "1000");
        }
    }

    @Test
    void qifOpeningIsAppliedOnceAndConflictingLedgersAreRejected() throws Exception {
        long cash = cash("EUR", "0", START);
        importQif(cash);
        money(json("GET", "/accounts/" + cash, null, owner, 200), "ownBalance", "490");
        importQif(cash);
        money(json("GET", "/accounts/" + cash, null, owner, 200), "ownBalance", "490");
        long conflict = cash("EUR", "100", START);
        assertThatThrownBy(() -> importQif(conflict))
                .hasMessageContaining("opening balance conflicts");
        money(json("GET", "/accounts/" + conflict, null, owner, 200), "ownBalance", "100");
    }

    private void importQif(long account) throws Exception {
        EncryptionContext.setKey(keys.getKeyBySessionToken(owner.session()).orElseThrow());
        String qif =
                "!Type:Bank\nD01/01/"
                        + LocalDate.now().getYear()
                        + "\nT500.00\nPOpening Balance\nL[Audit cash]\n^\nD"
                        + LocalDate.now().getMonthValue()
                        + "/01/"
                        + LocalDate.now().getYear()
                        + "\nT-10.00\nPFood\nLFood\n^\n";
        String path =
                files.storeFile(
                        new MockMultipartFile(
                                "file",
                                "round-two.qif",
                                "application/qif",
                                qif.getBytes(StandardCharsets.UTF_8)),
                        owner.id());
        ImportSession session = imports.startImport(path, owner.id(), account, "round-two.qif");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            session = imports.getSession(session.getId(), owner.id());
            if (session.getStatus() == ImportSession.ImportStatus.PARSED) break;
            Thread.sleep(50);
        }
        assertThat(session.getStatus()).isEqualTo(ImportSession.ImportStatus.PARSED);
        imports.confirmImport(session.getId(), owner.id(), account, Map.of(), true);
    }
}
