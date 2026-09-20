package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openfinance.entity.ExchangeRate;
import org.openfinance.repository.ExchangeRateRepository;
import org.openfinance.security.EncryptionContext;
import org.openfinance.security.EncryptionKeyCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;

/** Accounting regressions for the September finance audit, against real encrypted databases. */
class FinanceCorrectnessIntegrationTest extends AuditApiTestSupport {
    @Autowired ExchangeRateRepository rates;
    @Autowired CacheManager caches;
    @Autowired EncryptionKeyCache keys;
    @Autowired AccountService accounts;
    @Autowired org.openfinance.repository.AccountRepository accountRepository;
    @Autowired TransactionService transactions;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired FileStorageService fileStorage;
    @Autowired ImportService imports;

    @BeforeEach
    void configureBase() {
        jdbc.update("UPDATE users SET base_currency = 'EUR' WHERE id = ?", owner.id());
        caches.getCacheNames().forEach(name -> caches.getCache(name).clear());
    }

    long category(String name) throws Exception {
        return json("POST", "/categories", data("name", name, "type", "EXPENSE"), owner, 201)
                .path("id")
                .asLong();
    }

    Map<String, Object> assetPayload(int quantity) {
        return data(
                "name",
                "Audit physical asset",
                "type",
                "FURNITURE",
                "quantity",
                quantity,
                "purchasePrice",
                1000,
                "currentPrice",
                1000,
                "currency",
                "EUR",
                "purchaseDate",
                START.toString());
    }

    void rate(LocalDate date, String value) {
        ExchangeRate row =
                rates.findByBaseCurrencyAndTargetCurrencyAndRateDate("USD", "EUR", date)
                        .orElse(
                                ExchangeRate.builder()
                                        .baseCurrency("USD")
                                        .targetCurrency("EUR")
                                        .rateDate(date)
                                        .source("audit-fixed")
                                        .build());
        row.setRate(new BigDecimal(value));
        rates.saveAndFlush(row);
        caches.getCache("exchangeRates").clear();
    }

    @Test
    void ordinaryExpenseCannotBecomeAnUnpairedTransfer() throws Exception {
        long source = account(owner), target = account(owner);
        Map<String, Object> payload = movement(source, 100, LocalDate.now());
        long tx = json("POST", "/transactions", payload, owner, 201).path("id").asLong();
        payload.putAll(data("type", "TRANSFER", "toAccountId", target));
        JsonNode changed = json("PUT", "/transactions/" + tx, payload, owner, 400);
        JsonNode a = json("GET", "/accounts/" + source, null, owner, 200);
        JsonNode b = json("GET", "/accounts/" + target, null, owner, 200);
        money(a, "ownBalance", "900");
        money(b, "ownBalance", "1000");
        assertThat(json("GET", "/transactions/" + tx, null, owner, 200).path("type").asText())
                .isEqualTo("EXPENSE");
    }

    @Test
    void categoryAndCashflowReportsUseTheDatedReportingCurrency() throws Exception {
        rate(START, "0.5");
        rate(LocalDate.now(), "0.8");
        long cash = account(owner), cat = category("FX spending");
        Map<String, Object> payload = movement(cash, 100, START.plusMonths(1));
        payload.putAll(data("currency", "USD", "categoryId", cat));
        json("POST", "/transactions", payload, owner, 201);
        JsonNode balance = json("GET", "/accounts/" + cash, null, owner, 200);
        JsonNode spending = json("GET", "/dashboard/spending?period=365", null, owner, 200);
        JsonNode flow = json("GET", "/dashboard/cashflow?period=365", null, owner, 200);
        money(balance, "ownBalance", "950");
        money(spending, "Category_" + cat, "50");
        money(flow, "expenses", "50");
    }

    @Test
    void categoryReportsHonorSplitAllocations() throws Exception {
        long cash = account(owner), catA = category("Audit food"), catB = category("Audit travel");
        Map<String, Object> payload = movement(cash, 100, LocalDate.now());
        payload.put(
                "splits",
                List.of(
                        data("categoryId", catA, "amount", 40),
                        data("categoryId", catB, "amount", 60)));
        json("POST", "/transactions", payload, owner, 201);
        JsonNode spending = json("GET", "/dashboard/spending?period=30", null, owner, 200);
        JsonNode sankey = json("GET", "/dashboard/cashflow-sankey?period=30", null, owner, 200);
        money(spending, "Category_" + catA, "40");
        money(spending, "Category_" + catB, "60");
        assertThat(spending.has("Uncategorized")).isFalse();
        assertThat(sankey.path("expenseCategories")).hasSize(2);
        money(sankey, "totalExpenses", "100");
    }

    @Test
    void clearingTransactionCategoryPersistsNull() throws Exception {
        long cash = account(owner), cat = category("Clear me");
        Map<String, Object> payload = movement(cash, 100, LocalDate.now());
        payload.put("categoryId", cat);
        long tx = json("POST", "/transactions", payload, owner, 201).path("id").asLong();
        payload.put("categoryId", null);
        JsonNode changed = json("PUT", "/transactions/" + tx, payload, owner, 200);
        assertThat(changed.path("categoryId").isNull()).isTrue();
    }

    @Test
    void physicalImprovementPreservesHistoricalWealth() throws Exception {
        long cash = account(owner);
        long asset = json("POST", "/assets", assetPayload(1), owner, 201).path("id").asLong();
        Map<String, Object> payload = movement(cash, 100, START.plusMonths(1));
        payload.putAll(data("assetId", asset, "movementType", "CAPITAL_IMPROVEMENT"));
        json("POST", "/transactions", payload, owner, 201);
        JsonNode holding = json("GET", "/assets/" + asset, null, owner, 200);
        JsonNode historical = history(START.plusMonths(1), START.plusMonths(1));
        money(holding, "currentPrice", "1100");
        money(historical.get(0), "netWorth", "2000");
    }

    @Test
    void improvementPreservesCapitalAcrossManyUnits() throws Exception {
        long cash = account(owner);
        long asset = json("POST", "/assets", assetPayload(1000), owner, 201).path("id").asLong();
        Map<String, Object> payload = movement(cash, 1, LocalDate.now());
        payload.putAll(data("assetId", asset, "movementType", "CAPITAL_IMPROVEMENT"));
        json("POST", "/transactions", payload, owner, 201);
        JsonNode holding = json("GET", "/assets/" + asset, null, owner, 200);
        money(holding, "totalValue", "1000001");
        money(json("GET", "/accounts/" + cash, null, owner, 200), "ownBalance", "999");
    }

    @Test
    void loanStartCannotMoveAfterARecordedRepayment() throws Exception {
        long cash = account(owner), loan = loan(START);
        Map<String, Object> payload = movement(cash, 100, START.plusMonths(1));
        payload.put("liabilityId", loan);
        json("POST", "/transactions", payload, owner, 201);
        money(history(START.plusMonths(1), START.plusMonths(1)).get(0), "netWorth", "0");
        JsonNode changed =
                json(
                        "PUT",
                        "/liabilities/" + loan,
                        data(
                                "name",
                                "Loan",
                                "type",
                                "PERSONAL_LOAN",
                                "principal",
                                1000,
                                "currentBalance",
                                900,
                                "currency",
                                "EUR",
                                "startDate",
                                START.plusMonths(2).toString(),
                                "interestRate",
                                0),
                        owner,
                        400);
        JsonNode historical = history(START.plusMonths(1), START.plusMonths(1));
        money(historical.get(0), "netWorth", "0");
    }

    @Test
    void zeroWealthMonthsRemainInHistory() throws Exception {
        long cash = account(owner);
        json("POST", "/transactions", movement(cash, 1000, START.plusMonths(1)), owner, 201);
        JsonNode historical = history(START, START.plusMonths(2));
        assertThat(historical.size()).isEqualTo(3);
        money(historical.get(1), "netWorth", "0");
        money(historical.get(2), "netWorth", "0");
    }

    @Test
    void plannedAssetsAreExcludedFromPortfolioPerformance() throws Exception {
        Map<String, Object> payload = assetPayload(1);
        payload.putAll(
                data(
                        "acquisitionType",
                        "PLANNED",
                        "currentPrice",
                        0,
                        "purchaseDate",
                        LocalDate.now().plusMonths(1).toString()));
        json("POST", "/assets", payload, owner, 201);
        JsonNode portfolio =
                json("GET", "/dashboard/portfolio-performance?period=30", null, owner, 200);
        assertThat(portfolio).isEmpty();
    }

    @Test
    void recordedLoanChargesAppearInAmountsPaid() throws Exception {
        long cash = account(owner), loan = loan(START);
        int[] amounts = {25, 10, 5};
        String[] types = {"INTEREST", "FEE", "INSURANCE"};
        for (int i = 0; i < types.length; i++) {
            Map<String, Object> payload = movement(cash, amounts[i], LocalDate.now());
            payload.putAll(data("liabilityId", loan, "movementType", types[i]));
            json("POST", "/transactions", payload, owner, 201);
        }
        JsonNode breakdown = json("GET", "/liabilities/" + loan + "/breakdown", null, owner, 200);
        money(breakdown, "interestPaid", "25");
        money(breakdown, "feesPaid", "10");
        money(breakdown, "insurancePaid", "5");
        money(breakdown, "totalPaid", "40");
    }

    @Test
    void backdatedDirectDrawPreservesTheLaterEffectiveValuation() throws Exception {
        long loan = loan(START), property = property(owner).path("id").asLong();
        jdbc.update(
                "UPDATE real_estate_value_history SET effective_date = ? WHERE property_id = ?",
                START.toString(),
                property);
        json(
                "POST",
                "/liabilities/" + loan + "/disburse",
                data(
                        "directRealEstateId",
                        property,
                        "amount",
                        200,
                        "date",
                        START.plusMonths(2).toString()),
                owner,
                200);
        json(
                "POST",
                "/liabilities/" + loan + "/disburse",
                data(
                        "directRealEstateId",
                        property,
                        "amount",
                        100,
                        "date",
                        START.plusMonths(1).toString()),
                owner,
                200);
        JsonNode current = json("GET", "/real-estate/" + property, null, owner, 200);
        JsonNode historical = history(START.plusMonths(2), START.plusMonths(2));
        money(current, "currentValue", "200");
        money(historical.get(0), "totalAssets", "200");
    }

    @Test
    void sameCurrencyTransferPreservesTheExactTotal() throws Exception {
        Map<String, Object> payload =
                data(
                        "name",
                        "Precision audit",
                        "type",
                        "CHECKING",
                        "currency",
                        "EUR",
                        "initialBalance",
                        1,
                        "openingDate",
                        START.toString());
        long source = json("POST", "/accounts", payload, owner, 201).path("id").asLong();
        long destination = json("POST", "/accounts", payload, owner, 201).path("id").asLong();
        JsonNode transfer =
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
                                "currency",
                                "EUR",
                                "amount",
                                new BigDecimal("0.12345678"),
                                "date",
                                LocalDate.now().toString()),
                        owner,
                        201);
        JsonNode a = json("GET", "/accounts/" + source, null, owner, 200);
        JsonNode b = json("GET", "/accounts/" + destination, null, owner, 200);
        money(a, "ownBalance", "0.87654322");
        money(b, "ownBalance", "1.12345678");
    }

    @Test
    void loanProceedsAreNotEarnedIncome() throws Exception {
        long cash = account(owner), loan = loan(START);
        Map<String, Object> draw = movement(cash, 1000, LocalDate.now());
        draw.putAll(data("type", "INCOME", "liabilityId", loan, "movementType", "DISBURSEMENT"));
        json("POST", "/transactions", draw, owner, 201);
        JsonNode capacity =
                json("GET", "/dashboard/borrowing-capacity?period=30", null, owner, 200);
        money(capacity, "monthlyIncome", "0");
        money(capacity, "availableBorrowingCapacity", "0");
        assertThat(capacity.path("financialHealthStatus").asText()).isEqualTo("INSUFFICIENT_DATA");
    }

    @Test
    void preciseFrenchCsvIsPersistedWithoutInflation() throws Exception {
        long cash = account(owner);
        EncryptionContext.setKey(keys.getKeyBySessionToken(owner.session()).orElseThrow());
        String csv =
                "Date;Montant;Devise;Libelle\n"
                        + START.plusMonths(1)
                        + ";-12,3456;EUR;Precision audit\n";
        String upload =
                fileStorage.storeFile(
                        new org.springframework.mock.web.MockMultipartFile(
                                "file",
                                "audit.csv",
                                "text/csv",
                                csv.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                        owner.id());
        org.openfinance.entity.ImportSession session =
                imports.startImport(upload, owner.id(), cash, "audit.csv");
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            session = imports.getSession(session.getId(), owner.id());
            if (session.getStatus() == org.openfinance.entity.ImportSession.ImportStatus.PARSED)
                break;
            Thread.sleep(50);
        }
        assertThat(session.getStatus())
                .isEqualTo(org.openfinance.entity.ImportSession.ImportStatus.PARSED);
        assertThat(imports.reviewTransactions(session.getId(), owner.id()).getFirst().getAmount())
                .isEqualByComparingTo("-12.3456");
        org.openfinance.entity.ImportSession result =
                imports.confirmImport(session.getId(), owner.id(), cash, Map.of(), true);
        assertThat(result.getImportedCount()).isEqualTo(1);
        JsonNode balance = json("GET", "/accounts/" + cash, null, owner, 200);
        money(balance, "ownBalance", "987.6544");
    }

    @Test
    void concurrentApiPostingsPreserveBothDebits() throws Exception {
        long cash = account(owner);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<JsonNode> post =
                    () -> {
                        start.await();
                        return json(
                                "POST",
                                "/transactions",
                                movement(cash, 100, LocalDate.now()),
                                owner,
                                201);
                    };
            var first = executor.submit(post);
            var second = executor.submit(post);
            start.countDown();
            assertThat(first.get(30, java.util.concurrent.TimeUnit.SECONDS).path("id").asLong())
                    .isNotEqualTo(
                            second.get(30, java.util.concurrent.TimeUnit.SECONDS)
                                    .path("id")
                                    .asLong());
        }
        money(json("GET", "/accounts/" + cash, null, owner, 200), "ownBalance", "800");
    }

    @Test
    void staleOuterTransactionCannotCommitALostAccountUpdate() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                System.getenv()
                        .getOrDefault("SPRING_DATASOURCE_URL", "")
                        .startsWith("jdbc:postgresql:"));
        long cash = account(owner);
        javax.crypto.SecretKey key = keys.getKeyBySessionToken(owner.session()).orElseThrow();
        var bothRead = new java.util.concurrent.CountDownLatch(2);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Long> post =
                    () -> {
                        EncryptionContext.setKey(key);
                        try {
                            return new org.springframework.transaction.support.TransactionTemplate(
                                            transactionManager)
                                    .execute(
                                            status -> {
                                                accountRepository
                                                        .findByIdAndUserId(cash, owner.id())
                                                        .orElseThrow();
                                                bothRead.countDown();
                                                try {
                                                    if (!bothRead.await(
                                                            10,
                                                            java.util.concurrent.TimeUnit.SECONDS))
                                                        throw new IllegalStateException(
                                                                "barrier timeout");
                                                } catch (InterruptedException exception) {
                                                    throw new RuntimeException(exception);
                                                }
                                                return transactions
                                                        .createTransaction(
                                                                owner.id(),
                                                                org.openfinance.dto
                                                                        .TransactionRequest
                                                                        .builder()
                                                                        .accountId(cash)
                                                                        .type(
                                                                                org.openfinance
                                                                                        .entity
                                                                                        .TransactionType
                                                                                        .EXPENSE)
                                                                        .amount(
                                                                                new BigDecimal(
                                                                                        "100"))
                                                                        .currency("EUR")
                                                                        .date(LocalDate.now())
                                                                        .description(
                                                                                "Concurrent expense")
                                                                        .build())
                                                        .getId();
                                            });
                        } finally {
                            EncryptionContext.clear();
                        }
                    };
            var first = executor.submit(post);
            var second = executor.submit(post);
            int committed = 0;
            for (var future : List.of(first, second)) {
                try {
                    future.get(30, java.util.concurrent.TimeUnit.SECONDS);
                    committed++;
                } catch (java.util.concurrent.ExecutionException exception) {
                    assertThat(exception.getCause())
                            .isInstanceOf(
                                    org.springframework.dao.OptimisticLockingFailureException
                                            .class);
                }
            }
            assertThat(committed).isEqualTo(1);
        }
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM transactions WHERE user_id = ?",
                                Integer.class,
                                owner.id()))
                .isEqualTo(1);
        money(json("GET", "/accounts/" + cash, null, owner, 200), "ownBalance", "900");
        json("POST", "/transactions", movement(cash, 100, LocalDate.now()), owner, 201);
        money(json("GET", "/accounts/" + cash, null, owner, 200), "ownBalance", "800");
    }

    @Test
    void fractionalPerUnitImprovementAndItsEditAndDeletionPreserveTotalAndHistory()
            throws Exception {
        long cash = account(owner);
        long asset = json("POST", "/assets", assetPayload(3), owner, 201).path("id").asLong();
        Map<String, Object> payload = movement(cash, 1, START.plusMonths(1));
        payload.putAll(data("assetId", asset, "movementType", "CAPITAL_IMPROVEMENT"));
        long tx = json("POST", "/transactions", payload, owner, 201).path("id").asLong();
        money(json("GET", "/assets/" + asset, null, owner, 200), "totalValue", "3001");
        money(history(START.plusMonths(1), START.plusMonths(1)).get(0), "netWorth", "4000");
        payload.put("amount", 2);
        json("PUT", "/transactions/" + tx, payload, owner, 200);
        money(json("GET", "/assets/" + asset, null, owner, 200), "totalValue", "3002");
        money(history(START.plusMonths(1), START.plusMonths(1)).get(0), "netWorth", "4000");
        json("DELETE", "/transactions/" + tx, null, owner, 204);
        money(json("GET", "/assets/" + asset, null, owner, 200), "totalValue", "3000");
        money(json("GET", "/accounts/" + cash, null, owner, 200), "ownBalance", "1000");
        money(history(START.plusMonths(1), START.plusMonths(1)).get(0), "netWorth", "4000");
    }

    @Test
    void loanChargesAreRecordedFromRepaymentSplitsAndNotFromConfiguredFees() throws Exception {
        long cash = account(owner), loan = loan(START);
        long interest = category("Loan interest"), insurance = category("Loan insurance");
        jdbc.update(
                "UPDATE categories SET name_key = 'category.interest.expense' WHERE id = ?",
                interest);
        jdbc.update(
                "UPDATE categories SET name_key = 'category.insurance' WHERE id = ?", insurance);
        Map<String, Object> payload = movement(cash, 100, START.plusMonths(1));
        payload.putAll(
                data(
                        "liabilityId",
                        loan,
                        "movementType",
                        "REPAYMENT",
                        "splits",
                        List.of(
                                data("amount", 70),
                                data("categoryId", interest, "amount", 25),
                                data("categoryId", insurance, "amount", 5))));
        long tx = json("POST", "/transactions", payload, owner, 201).path("id").asLong();
        JsonNode breakdown = json("GET", "/liabilities/" + loan + "/breakdown", null, owner, 200);
        money(breakdown, "principalPaid", "70");
        money(breakdown, "interestPaid", "25");
        money(breakdown, "insurancePaid", "5");
        money(breakdown, "totalPaid", "100");
        json("DELETE", "/transactions/" + tx, null, owner, 204);
        money(
                json("GET", "/liabilities/" + loan + "/breakdown", null, owner, 200),
                "totalPaid",
                "0");
    }
}
