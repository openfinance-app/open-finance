package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openfinance.entity.Transaction;
import org.openfinance.repository.LiabilityRepository;
import org.openfinance.repository.TransactionRepository;
import org.openfinance.security.EncryptionContext;
import org.openfinance.security.EncryptionKeyCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "application.import.skrooge-json-enabled=true")
class LiabilityConversionIntegrationTest extends AuditApiTestSupport {
    @Autowired TransactionRepository transactions;
    @Autowired LiabilityRepository liabilities;
    @Autowired NetWorthService netWorth;
    @Autowired EncryptionKeyCache keys;

    @Test
    void detectsOlderImportsAndReusesTheirActualDebtHistory() throws Exception {
        Fixture source = fixture();
        jdbc.update("UPDATE accounts SET import_source = NULL WHERE id = ?", source.account());
        EncryptionContext.setKey(keys.getKeyBySessionToken(owner.session()).orElseThrow());
        try {
            Transaction movement = transactions.findById(source.payment()).orElseThrow();
            movement.setExternalReference("skrooge:operation:42");
            transactions.saveAndFlush(movement);
        } finally {
            EncryptionContext.clear();
        }
        JsonNode converted = convert(source);
        EncryptionContext.setKey(keys.getKeyBySessionToken(owner.session()).orElseThrow());
        try {
            org.openfinance.entity.Liability loan =
                    liabilities.findById(converted.path("id").asLong()).orElseThrow();
            assertThat(netWorth.getLiabilityBalanceAt(loan, START)).isEqualByComparingTo("1000");
            assertThat(netWorth.getLiabilityBalanceAt(loan, START.plusDays(2)))
                    .isEqualByComparingTo("1200");
            assertThat(
                            netWorth.getStandaloneLiabilityBalanceHistory(
                                    loan, START, START.plusDays(4)))
                    .extracting(org.openfinance.dto.BalanceHistoryPoint::balance)
                    .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .containsExactly(
                            new BigDecimal("1000"),
                            new BigDecimal("1200"),
                            new BigDecimal("1200"),
                            new BigDecimal("800"),
                            new BigDecimal("800"));
        } finally {
            EncryptionContext.clear();
        }
    }

    @Test
    void preservesSourceCurrencyAndDecimalPrecision() throws Exception {
        String debt = "800.12345678";
        long source =
                json(
                                "POST",
                                "/accounts",
                                data(
                                        "name",
                                        "Imported foreign debt",
                                        "type",
                                        "OTHER",
                                        "currency",
                                        "XOF",
                                        "initialBalance",
                                        "-" + debt,
                                        "openingDate",
                                        START),
                                owner,
                                201)
                        .path("id")
                        .asLong();
        markImported(source);
        JsonNode candidate =
                json("GET", "/liabilities/conversion/candidates", null, owner, 200).get(0);
        assertThat(candidate.path("currency").asText()).isEqualTo("XOF");
        assertThat(candidate.path("exactValues").path("outstandingBalance").asText())
                .isEqualTo(debt);
        Map<String, Object> terms = terms();
        terms.put("currency", "XOF");
        terms.put("currentBalance", debt);
        JsonNode result =
                json(
                        "POST",
                        "/liabilities/conversion",
                        data(
                                "operationId",
                                UUID.randomUUID(),
                                "accountId",
                                source,
                                "sourceRevision",
                                candidate.path("sourceRevision").asText(),
                                "fundedPrincipal",
                                1200,
                                "liability",
                                terms),
                        owner,
                        201);
        assertThat(result.path("currency").asText()).isEqualTo("XOF");
        money(result, "currentBalance", debt);
    }

    @BeforeEach
    void reportingCurrency() {
        jdbc.update("UPDATE users SET base_currency = 'EUR' WHERE id = ?", owner.id());
    }

    @Test
    void movesDebtOncePreservingCashHistoryAndOriginalTransactions() throws Exception {
        Fixture source = fixture();
        List<LocalDate> dates =
                List.of(START, START.plusDays(2), START.plusDays(4), LocalDate.now());
        List<BigDecimal> before = new java.util.ArrayList<>();
        for (LocalDate date : dates)
            before.add(history(date, date).get(0).path("netWorth").decimalValue());
        JsonNode original = json("GET", "/transactions/" + source.payment(), null, owner, 200);
        JsonNode loan = convert(source);
        money(loan, "currentBalance", "800");
        money(loan, "fundedAmount", "1200");
        money(loan, "principalPaid", "400");
        assertThat(loan.path("convertedFromAccountId").asLong()).isEqualTo(source.account());
        assertThat(loan.path("balanceLocked").asBoolean()).isTrue();
        assertThat(loan.path("balanceStartDate").asText()).isEqualTo(LocalDate.now().toString());
        JsonNode account = json("GET", "/accounts/" + source.account(), null, owner, 200);
        money(account, "ownBalance", "0");
        assertThat(account.path("isActive").asBoolean()).isFalse();
        assertThat(count("transactions")).isEqualTo(2);
        JsonNode preserved = json("GET", "/transactions/" + source.payment(), null, owner, 200);
        for (String field :
                List.of(
                        "amount",
                        "type",
                        "date",
                        "currency",
                        "originalAmount",
                        "originalCurrency",
                        "transferId",
                        "liabilityId"))
            assertThat(preserved.path(field)).as(field).isEqualTo(original.path(field));
        for (int i = 0; i < dates.size(); i++)
            assertThat(history(dates.get(i), dates.get(i)).get(0).path("netWorth").decimalValue())
                    .isEqualByComparingTo(before.get(i));
        assertThat(
                        json(
                                "GET",
                                "/liabilities/" + loan.path("id").asLong() + "/transactions",
                                null,
                                owner,
                                200))
                .hasSize(2);
        JsonNode filtered =
                json(
                        "GET",
                        "/transactions/search?liabilityId=" + loan.path("id").asLong(),
                        null,
                        owner,
                        200);
        assertThat(filtered.path("totalElements").asInt()).isEqualTo(2);
        assertThat(json("GET", "/liabilities/conversion/candidates", null, owner, 200)).isEmpty();
    }

    @Test
    void managedRepaymentsAndFurtherDrawsChangeOnlyTheNewDebt() throws Exception {
        Fixture source = fixture();
        long loan = convert(source).path("id").asLong();
        long cash = account(owner);
        Map<String, Object> payment = movement(cash, 100, LocalDate.now());
        payment.putAll(
                data("liabilityId", loan, "movementType", "REPAYMENT", "principalAmount", 100));
        json("POST", "/transactions", payment, owner, 201);
        money(json("GET", "/liabilities/" + loan, null, owner, 200), "currentBalance", "700");
        money(json("GET", "/accounts/" + cash, null, owner, 200), "ownBalance", "900");
        json(
                "POST",
                "/liabilities/" + loan + "/disburse",
                data("toAccountId", cash, "amount", 200, "date", LocalDate.now()),
                owner,
                200);
        JsonNode after = json("GET", "/liabilities/" + loan, null, owner, 200);
        money(after, "currentBalance", "900");
        money(after, "fundedAmount", "1400");
        money(after, "principalPaid", "500");
        money(json("GET", "/accounts/" + source.account(), null, owner, 200), "ownBalance", "0");
        money(history(LocalDate.now(), LocalDate.now()).get(0), "netWorth", "200");
        payment.put("date", LocalDate.now().minusDays(1));
        json("POST", "/transactions", payment, owner, 400);
        json(
                "POST",
                "/liabilities/" + loan + "/disburse",
                data("toAccountId", cash, "amount", 100, "date", LocalDate.now().minusDays(1)),
                owner,
                400);
    }

    @Test
    void linksHistoricalComponentsWithoutPayingOpeningDebtTwice() throws Exception {
        Fixture source = fixture();
        long cash = account(owner);
        Map<String, Object> payment = movement(cash, 100, START.plusDays(3));
        payment.put(
                "splits",
                List.of(
                        data("amount", 80, "loanComponent", "PRINCIPAL"),
                        data("amount", 12, "loanComponent", "INTEREST"),
                        data("amount", 6, "loanComponent", "INSURANCE"),
                        data("amount", 2, "loanComponent", "FEE")));
        long transaction = json("POST", "/transactions", payment, owner, 201).path("id").asLong();
        long loan = convert(source).path("id").asLong();
        JsonNode before = history(START, LocalDate.now());
        payment.putAll(data("liabilityId", loan, "movementType", "REPAYMENT"));
        JsonNode linked = json("PUT", "/transactions/" + transaction, payment, owner, 200);
        money(linked, "principalAmount", "80");
        long action =
                jdbc.queryForObject(
                        "SELECT MAX(id) FROM operation_history WHERE user_id = ?",
                        Long.class,
                        owner.id());
        for (String operation : List.of("undo", "redo"))
            json("POST", "/history/" + action + "/" + operation, null, owner, 200);
        money(json("GET", "/accounts/" + cash, null, owner, 200), "ownBalance", "900");
        JsonNode breakdown = json("GET", "/liabilities/" + loan + "/breakdown", null, owner, 200);
        money(breakdown, "currentBalance", "800");
        money(breakdown, "principalPaid", "400");
        money(breakdown, "interestPaid", "12");
        money(breakdown, "insurancePaid", "6");
        money(breakdown, "feesPaid", "2");
        money(breakdown, "totalPaid", "420");
        assertThat(history(START, LocalDate.now())).isEqualTo(before);
        payment.put("amount", 101);
        json("PUT", "/transactions/" + transaction, payment, owner, 400);
        payment.put("amount", 100);
        payment.put("date", LocalDate.now());
        json("PUT", "/transactions/" + transaction, payment, owner, 400);
        payment.put("date", START.plusDays(3));
        payment.remove("liabilityId");
        payment.remove("movementType");
        json("PUT", "/transactions/" + transaction, payment, owner, 200);
        money(json("GET", "/liabilities/" + loan, null, owner, 200), "currentBalance", "800");
        payment.putAll(data("liabilityId", loan, "movementType", "REPAYMENT"));
        json("PUT", "/transactions/" + transaction, payment, owner, 200);
        json("DELETE", "/transactions/" + transaction, null, owner, 204);
        money(json("GET", "/liabilities/" + loan, null, owner, 200), "currentBalance", "800");
    }

    @Test
    void existingInsuranceBeforeFirstDrawCanBeLinkedButNewBackdatedPostCannot() throws Exception {
        Fixture source = fixture();
        long cash =
                json(
                                "POST",
                                "/accounts",
                                data(
                                        "name",
                                        "Pre-offer cash",
                                        "type",
                                        "CHECKING",
                                        "currency",
                                        "EUR",
                                        "initialBalance",
                                        1000,
                                        "openingDate",
                                        START.minusMonths(1)),
                                owner,
                                201)
                        .path("id")
                        .asLong();
        Map<String, Object> payment = movement(cash, 15, START.minusDays(1));
        long id = json("POST", "/transactions", payment, owner, 201).path("id").asLong();
        long loan = convert(source).path("id").asLong();
        payment.putAll(data("liabilityId", loan, "movementType", "INSURANCE"));
        json("PUT", "/transactions/" + id, payment, owner, 200);
        money(
                json("GET", "/liabilities/" + loan + "/breakdown", null, owner, 200),
                "insurancePaid",
                "15");
        money(json("GET", "/liabilities/" + loan, null, owner, 200), "currentBalance", "800");
        json("POST", "/transactions", payment, owner, 400);
        json("PUT", "/transactions/" + id, payment, register(), 404);
    }

    @Test
    void distinguishesSameDayHistoryFromPaymentsRecordedAfterConversion() throws Exception {
        Fixture source = fixture();
        long cash = account(owner);
        Map<String, Object> payment = movement(cash, 80, LocalDate.now());
        long id = json("POST", "/transactions", payment, owner, 201).path("id").asLong();
        long loan = convert(source).path("id").asLong();
        payment.putAll(
                data("liabilityId", loan, "movementType", "REPAYMENT", "principalAmount", 80));
        json("PUT", "/transactions/" + id, payment, owner, 200);
        money(json("GET", "/liabilities/" + loan, null, owner, 200), "currentBalance", "800");
        money(json("GET", "/liabilities/" + loan, null, owner, 200), "principalPaid", "400");
        json("POST", "/transactions", payment, owner, 201);
        money(json("GET", "/liabilities/" + loan, null, owner, 200), "currentBalance", "720");
        money(json("GET", "/liabilities/" + loan, null, owner, 200), "principalPaid", "480");
    }

    @Test
    void retriesAndUndoRedoAreSafe() throws Exception {
        Fixture source = fixture();
        Map<String, Object> request = request(source);
        long id = json("POST", "/liabilities/conversion", request, owner, 201).path("id").asLong();
        long action =
                jdbc.queryForObject(
                        "SELECT MAX(id) FROM operation_history WHERE user_id = ?",
                        Long.class,
                        owner.id());
        assertThat(json("POST", "/liabilities/conversion", request, owner, 201).path("id").asLong())
                .isEqualTo(id);
        assertThat(count("liabilities")).isEqualTo(1);
        Map<String, Object> changed = new java.util.LinkedHashMap<>(request);
        changed.put("fundedPrincipal", 1300);
        json("POST", "/liabilities/conversion", changed, owner, 400);
        json("POST", "/history/" + action + "/undo", null, owner, 200);
        money(json("GET", "/accounts/" + source.account(), null, owner, 200), "ownBalance", "-800");
        assertThat(count("liabilities")).isZero();
        assertThat(json("GET", "/liabilities/conversion/candidates", null, owner, 200)).hasSize(1);
        json("POST", "/liabilities/conversion", request, owner, 400);
        json("POST", "/history/" + action + "/redo", null, owner, 200);
        money(json("GET", "/liabilities/" + id, null, owner, 200), "currentBalance", "800");
        money(json("GET", "/accounts/" + source.account(), null, owner, 200), "ownBalance", "0");
    }

    @Test
    void archivedSourceCannotBeReopenedEditedOrDeletedAndDebtCannotBeReset() throws Exception {
        Fixture source = fixture();
        long id = convert(source).path("id").asLong();
        json("POST", "/accounts/" + source.account() + "/reopen", null, owner, 400);
        json(
                "PUT",
                "/accounts/" + source.account(),
                data("name", "Changed", "type", "OTHER", "currency", "EUR", "initialBalance", 0),
                owner,
                400);
        json("DELETE", "/accounts/" + source.account() + "/permanent", null, owner, 400);
        json("DELETE", "/transactions/" + source.payment(), null, owner, 400);
        json("DELETE", "/liabilities/" + id, null, owner, 400);
        Map<String, Object> terms = terms();
        terms.put("currentBalance", 700);
        json("PUT", "/liabilities/" + id, terms, owner, 409);
        terms.put("currentBalance", 800);
        terms.put("currency", "USD");
        json("PUT", "/liabilities/" + id, terms, owner, 400);
        money(json("GET", "/liabilities/" + id, null, owner, 200), "currentBalance", "800");
    }

    @Test
    void rejectsStaleReviewAndForeignSourcesWithoutSideEffects() throws Exception {
        Fixture source = fixture();
        Map<String, Object> request = request(source);
        Auth other = register();
        assertThat(json("GET", "/liabilities/conversion/candidates", null, other, 200)).isEmpty();
        json("POST", "/liabilities/conversion", request, other, 404);
        json(
                "POST",
                "/transactions",
                movement(source.account(), 20, START.plusDays(5)),
                owner,
                201);
        json("POST", "/liabilities/conversion", request, owner, 400);
        assertThat(count("liabilities")).isZero();
        assertThat(count("liability_conversion_receipts")).isZero();
        money(json("GET", "/accounts/" + source.account(), null, owner, 200), "ownBalance", "-820");
    }

    @Test
    void acceptsImportedOpeningOnlyDebtButExcludesOrdinaryAndNonDebtAccounts() throws Exception {
        long source = debtAccount();
        assertThat(json("GET", "/liabilities/conversion/candidates", null, owner, 200)).isEmpty();
        markImported(source);
        JsonNode candidates = json("GET", "/liabilities/conversion/candidates", null, owner, 200);
        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).path("transactionCount").asInt()).isZero();
        long ordinary = account(owner);
        markImported(ordinary);
        assertThat(json("GET", "/liabilities/conversion/candidates", null, owner, 200)).hasSize(1);
    }

    @Test
    void invalidAmountsRollBackConversion() throws Exception {
        Fixture source = fixture();
        Map<String, Object> request = request(source);
        request.put("fundedPrincipal", 700);
        json("POST", "/liabilities/conversion", request, owner, 400);
        request.put("fundedPrincipal", 2500);
        json("POST", "/liabilities/conversion", request, owner, 400);
        request.put("fundedPrincipal", 1200);
        ((Map<String, Object>) request.get("liability")).put("currentBalance", 799);
        json("POST", "/liabilities/conversion", request, owner, 400);
        assertThat(count("liabilities")).isZero();
    }

    private long debtAccount() throws Exception {
        return json(
                        "POST",
                        "/accounts",
                        data(
                                "name",
                                "Imported mortgage",
                                "type",
                                "OTHER",
                                "currency",
                                "EUR",
                                "initialBalance",
                                -1000,
                                "openingDate",
                                START),
                        owner,
                        201)
                .path("id")
                .asLong();
    }

    private Fixture fixture() throws Exception {
        long source = debtAccount();
        markImported(source);
        json("POST", "/transactions", movement(source, 200, START.plusDays(1)), owner, 201);
        Map<String, Object> repayment = movement(source, 400, START.plusDays(3));
        repayment.put("type", "INCOME");
        long payment = json("POST", "/transactions", repayment, owner, 201).path("id").asLong();
        return new Fixture(source, payment);
    }

    private void markImported(long account) {
        jdbc.update("UPDATE accounts SET import_source = 'SKROOGE_JSON' WHERE id = ?", account);
    }

    private Map<String, Object> terms() {
        return data(
                "name",
                "Managed mortgage",
                "type",
                "MORTGAGE",
                "currency",
                "EUR",
                "principal",
                2000,
                "currentBalance",
                800,
                "startDate",
                START,
                "interestRate",
                0);
    }

    private Map<String, Object> request(Fixture source) throws Exception {
        JsonNode candidate =
                json("GET", "/liabilities/conversion/candidates", null, owner, 200).get(0);
        return data(
                "operationId",
                UUID.randomUUID().toString(),
                "accountId",
                source.account(),
                "sourceRevision",
                candidate.path("sourceRevision").asText(),
                "fundedPrincipal",
                1200,
                "liability",
                terms());
    }

    private JsonNode convert(Fixture source) throws Exception {
        return json("POST", "/liabilities/conversion", request(source), owner, 201);
    }

    private long count(String table) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE user_id = ?", Long.class, owner.id());
    }

    private record Fixture(long account, long payment) {}
}
