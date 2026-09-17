package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditFinancialIntegrityIntegrationTest extends AuditApiTestSupport {
    @org.springframework.beans.factory.annotation.Autowired
    private org.openfinance.repository.AccountStatusHistoryRepository statusHistory;

    private void dateLatestStatus(LocalDate date) {
        org.openfinance.entity.AccountStatusHistory event =
                statusHistory.findByUserId(owner.id()).stream()
                        .max(
                                java.util.Comparator.comparing(
                                        org.openfinance.entity.AccountStatusHistory::getId))
                        .orElseThrow();
        event.setEffectiveDate(date);
        statusHistory.saveAndFlush(event);
    }

    @Test
    void foreignCurrencyConversionMustReconcileWithinDeclaredRounding() throws Exception {
        long cash = account(owner);
        Map<String, Object> payload = movement(cash, 10, LocalDate.now());
        payload.putAll(
                data("originalAmount", 100, "originalCurrency", "USD", "conversionRate", 0.9));
        json("POST", "/transactions", payload, owner, 400);
        payload.put("amount", 90);
        long id = json("POST", "/transactions", payload, owner, 201).path("id").asLong();
        payload.put("conversionRate", 0.8);
        json("PUT", "/transactions/" + id, payload, owner, 400);
        money(json("GET", "/accounts/" + cash, null, owner, 200), "ownBalance", "910");
        json("DELETE", "/transactions/" + id, null, owner, 204);
        money(json("GET", "/accounts/" + cash, null, owner, 200), "ownBalance", "1000");
    }

    @Test
    void transactionEditsInvalidateCachedHistoryFromTheEarlierDate() throws Exception {
        long cash = account(owner);
        LocalDate earlier = START.plusMonths(1);
        LocalDate later = earlier.plusMonths(1);
        Map<String, Object> payload = movement(cash, 100, later);
        long id = json("POST", "/transactions", payload, owner, 201).path("id").asLong();
        money(history(earlier, earlier).get(0), "netWorth", "1000");
        payload.put("date", earlier.toString());
        json("PUT", "/transactions/" + id, payload, owner, 200);
        JsonNode cached =
                json(
                        "GET",
                        "/dashboard/networth-history?startDate=" + earlier + "&endDate=" + earlier,
                        null,
                        owner,
                        200);
        money(cached.get(0), "netWorth", "900");
    }

    @Test
    void accountInclusionFollowsCloseAndReopenIntervals() throws Exception {
        long cash = account(owner);
        property(owner);
        LocalDate closed = START.plusMonths(1);
        LocalDate reopened = closed.plusMonths(1);
        json("POST", "/accounts/" + cash + "/close", null, owner, 204);
        dateLatestStatus(closed);
        json("POST", "/accounts/" + cash + "/reopen", null, owner, 204);
        dateLatestStatus(reopened);
        money(history(START, START).get(0), "netWorth", "2000");
        money(history(closed, closed).get(0), "netWorth", "1000");
        money(history(reopened, reopened).get(0), "netWorth", "2000");
    }

    @Test
    void backupRestoresAccountLifecycleAndDatedImprovements() throws Exception {
        assumeTrue(
                !System.getenv()
                        .getOrDefault("SPRING_DATASOURCE_URL", "")
                        .startsWith("jdbc:postgresql:"));
        long cash = account(owner);
        long property = property(owner).path("id").asLong();
        Map<String, Object> payload = movement(cash, 100, START.plusMonths(1));
        payload.putAll(data("realEstateId", property, "movementType", "CAPITAL_IMPROVEMENT"));
        json("POST", "/transactions", payload, owner, 201);
        json("POST", "/accounts/" + cash + "/close", null, owner, 204);
        JsonNode backup =
                json("POST", "/backup/create", data("description", "Dated history"), owner, 201);
        json(
                "POST",
                "/backup/restore/" + backup.path("id").asLong(),
                data("masterPassword", MASTER),
                owner,
                200);
        money(history(START, START).get(0), "netWorth", "2000");
        money(history(START.plusMonths(1), START.plusMonths(1)).get(0), "netWorth", "2000");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM account_status_history WHERE user_id = ?",
                                Integer.class,
                                owner.id()))
                .isEqualTo(1);
    }

    @Test
    void inconsistentConversionCannotReduceMoreDebtThanCash() throws Exception {
        long cash = account(owner);
        long loan = loan(START);
        Map<String, Object> payload = movement(cash, 10, LocalDate.now());
        payload.putAll(
                data(
                        "liabilityId",
                        loan,
                        "originalAmount",
                        100,
                        "originalCurrency",
                        "EUR",
                        "conversionRate",
                        2));
        json("POST", "/transactions", payload, owner, 400);
        payload.put("amount", 100);
        payload.put("conversionRate", 1);
        long id = json("POST", "/transactions", payload, owner, 201).path("id").asLong();
        payload.put("amount", 10);
        json("PUT", "/transactions/" + id, payload, owner, 400);
        money(json("GET", "/accounts/" + cash, null, owner, 200), "ownBalance", "900");
        money(json("GET", "/liabilities/" + loan, null, owner, 200), "currentBalance", "900");
        json("DELETE", "/transactions/" + id, null, owner, 204);
        money(json("GET", "/accounts/" + cash, null, owner, 200), "ownBalance", "1000");
        money(json("GET", "/liabilities/" + loan, null, owner, 200), "currentBalance", "1000");
    }

    @Test
    void capitalImprovementRequiresExpenseDirection() throws Exception {
        long cash = account(owner);
        long destination = account(owner);
        long property = property(owner).path("id").asLong();
        Map<String, Object> payload = movement(cash, 100, LocalDate.now());
        payload.putAll(
                data(
                        "realEstateId",
                        property,
                        "movementType",
                        "CAPITAL_IMPROVEMENT",
                        "type",
                        "INCOME"));
        json("POST", "/transactions", payload, owner, 400);
        payload.putAll(data("type", "TRANSFER", "toAccountId", destination));
        json("POST", "/transactions", payload, owner, 400);
        money(json("GET", "/accounts/" + cash, null, owner, 200), "ownBalance", "1000");
        money(json("GET", "/real-estate/" + property, null, owner, 200), "currentValue", "1000");
    }

    @Test
    void outOfOrderImprovementsAndCorrectionsPreserveHistoricalWealth() throws Exception {
        long cash = account(owner);
        long property = property(owner).path("id").asLong();
        LocalDate earlier = LocalDate.now().minusMonths(2).withDayOfMonth(1);
        LocalDate later = earlier.plusMonths(1);
        Map<String, Object> laterPayload = movement(cash, 200, later);
        laterPayload.putAll(data("realEstateId", property, "movementType", "CAPITAL_IMPROVEMENT"));
        long laterId = json("POST", "/transactions", laterPayload, owner, 201).path("id").asLong();
        Map<String, Object> earlierPayload = movement(cash, 100, earlier);
        earlierPayload.putAll(
                data("realEstateId", property, "movementType", "CAPITAL_IMPROVEMENT"));
        long earlierId =
                json("POST", "/transactions", earlierPayload, owner, 201).path("id").asLong();
        assertHistory(earlier, later, "2000");
        laterPayload.putAll(data("date", earlier.minusMonths(1).toString(), "amount", 300));
        json("PUT", "/transactions/" + laterId, laterPayload, owner, 200);
        assertHistory(earlier.minusMonths(1), later, "2000");
        json("DELETE", "/transactions/" + earlierId, null, owner, 204);
        assertHistory(earlier.minusMonths(1), later, "2000");
        money(json("GET", "/real-estate/" + property, null, owner, 200), "currentValue", "1300");
        money(json("GET", "/accounts/" + cash, null, owner, 200), "ownBalance", "700");
    }

    private void assertHistory(LocalDate start, LocalDate end, String expected) throws Exception {
        JsonNode rows = history(start, end);
        assertThat(rows.size()).isGreaterThanOrEqualTo(2);
        for (JsonNode row : rows) money(row, "netWorth", expected);
    }

    @Test
    void closingAndReopeningDoNotEraseEarlierAccountHistory() throws Exception {
        long cash = account(owner);
        property(owner);
        money(history(START, START).get(0), "netWorth", "2000");
        json("POST", "/accounts/" + cash + "/close", null, owner, 204);
        money(history(START, START).get(0), "netWorth", "2000");
        money(
                json("GET", "/dashboard/summary", null, owner, 200).path("netWorth"),
                "netWorth",
                "1000");
        json("POST", "/accounts/" + cash + "/reopen", null, owner, 204);
        money(history(START, START).get(0), "netWorth", "2000");
        money(
                json("GET", "/dashboard/summary", null, owner, 200).path("netWorth"),
                "netWorth",
                "2000");
    }

    @Test
    void estimatingPropertyValueRefreshesWarmedSummaryAndRecordsValuation() throws Exception {
        account(owner);
        JsonNode property = property(owner);
        long id = property.path("id").asLong();
        money(
                json("GET", "/dashboard/summary", null, owner, 200).path("netWorth"),
                "netWorth",
                "2000");
        int before =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM real_estate_value_history WHERE property_id = ?",
                        Integer.class,
                        id);
        json("PUT", "/real-estate/" + id + "/value", 1500, owner, 200);
        money(
                json("GET", "/dashboard/summary", null, owner, 200).path("netWorth"),
                "netWorth",
                "2500");
        money(
                json("GET", "/assets/" + property.path("assetId").asLong(), null, owner, 200),
                "currentPrice",
                "1500");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM real_estate_value_history WHERE property_id = ?",
                                Integer.class,
                                id))
                .isEqualTo(before + 1);
    }

    @Test
    void genericLoanPostingsEnforceOriginationAndTodayBoundaries() throws Exception {
        long cash = account(owner);
        LocalDate start = LocalDate.now().minusMonths(1);
        long loan = loan(start);
        for (LocalDate date : new LocalDate[] {start.minusDays(1), LocalDate.now().plusDays(1)}) {
            json(
                    "POST",
                    "/liabilities/" + loan + "/disburse",
                    data("toAccountId", cash, "amount", 100, "date", date.toString()),
                    owner,
                    400);
            for (String type : new String[] {"INCOME", "EXPENSE"}) {
                Map<String, Object> payload = movement(cash, 100, date);
                payload.putAll(data("liabilityId", loan, "type", type));
                json("POST", "/transactions", payload, owner, 400);
            }
        }
        money(json("GET", "/liabilities/" + loan, null, owner, 200), "currentBalance", "1000");
        money(json("GET", "/accounts/" + cash, null, owner, 200), "ownBalance", "1000");
    }

    @Test
    void bindingErrorsAreClientErrors() throws Exception {
        json("POST", "/accounts", data("name", "Invalid", "type", "INVALID"), owner, 400);
        long property = property(owner).path("id").asLong();
        json("PUT", "/real-estate/" + property + "/value", null, owner, 400);
        json("GET", "/accounts/not-a-number", null, owner, 400);
    }

    @Test
    void failedForeignReferencesLeaveBothOwnersBackupsRestorable() throws Exception {
        assumeTrue(
                !System.getenv()
                        .getOrDefault("SPRING_DATASOURCE_URL", "")
                        .startsWith("jdbc:postgresql:"));
        Auth victim = register();
        long property = property(victim).path("id").asLong();
        Map<String, Object> payload = movement(account(owner), 10, LocalDate.now());
        payload.put("realEstateId", property);
        json("POST", "/transactions", payload, owner, 404);
        for (Auth user : new Auth[] {owner, victim}) {
            JsonNode backup =
                    json("POST", "/backup/create", data("description", "Audit restore"), user, 201);
            assertThat(backup.path("status").asText()).isEqualTo("COMPLETED");
            json(
                    "POST",
                    "/backup/restore/" + backup.path("id").asLong(),
                    data("masterPassword", MASTER),
                    user,
                    200);
        }
    }
}
