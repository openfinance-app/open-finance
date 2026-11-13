package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "application.import.skrooge-json-enabled=true")
class LiabilityConversionPaymentsIntegrationTest extends AuditApiTestSupport {
    private static final LocalDate ORIGIN = LocalDate.now().minusMonths(18).withDayOfMonth(17);

    @Test
    void convertsImportedMortgageAndAllSeventeenPaymentsAsOneUndoableOperation() throws Exception {
        long source = source();
        long cash = cash(owner);
        long property =
                json(
                                "POST",
                                "/real-estate",
                                data(
                                        "name",
                                        "Example residence",
                                        "propertyType",
                                        "RESIDENTIAL",
                                        "address",
                                        "1 Example Street",
                                        "purchasePrice",
                                        "208524.9987",
                                        "currentValue",
                                        "208524.9987",
                                        "currency",
                                        "EUR",
                                        "purchaseDate",
                                        ORIGIN),
                                owner,
                                201)
                        .path("id")
                        .asLong();
        createMortgagePayments(cash);
        JsonNode candidates = candidates(source);
        assertThat(candidates.path("items")).hasSize(17);
        assertThat(candidates.path("truncated").asBoolean()).isFalse();
        JsonNode oldCash = json("GET", "/accounts/" + cash, null, owner, 200);
        JsonNode oldHistory = history(ORIGIN.minusDays(12), ORIGIN.plusMonths(1));
        Map<String, Object> request = request(source, candidates);
        @SuppressWarnings("unchecked")
        Map<String, Object> terms = (Map<String, Object>) request.get("liability");
        terms.putAll(
                data(
                        "realEstateId",
                        property,
                        "interestRate",
                        "3.65",
                        "insurancePercentage",
                        "0.20",
                        "minimumPayment",
                        "1136.66",
                        "amortizationStartDate",
                        LocalDate.now().plusMonths(2).withDayOfMonth(5),
                        "endDate",
                        LocalDate.now().plusYears(24).withDayOfMonth(5),
                        "notes",
                        "TAEG 4.19%; 18 months deferred, then 282 payments"));
        List<JsonNode> originals = new ArrayList<>();
        for (JsonNode candidate : candidates.path("items"))
            originals.add(
                    json(
                            "GET",
                            "/transactions/" + candidate.path("transactionId").asLong(),
                            null,
                            owner,
                            200));
        long loan =
                json("POST", "/liabilities/conversion", request, owner, 201).path("id").asLong();
        long action =
                jdbc.queryForObject(
                        "SELECT MAX(id) FROM operation_history WHERE user_id = ?",
                        Long.class,
                        owner.id());
        assertThat(json("POST", "/liabilities/conversion", request, owner, 201).path("id").asLong())
                .isEqualTo(loan);
        assertMortgageTotals(loan);
        assertPaymentHistory(loan);
        money(
                json("GET", "/liabilities/" + loan, null, owner, 200),
                "monthlyInsuranceCost",
                "35.83");
        assertThat(
                        json("GET", "/real-estate/" + property, null, owner, 200)
                                .path("mortgageId")
                                .asLong())
                .isEqualTo(loan);
        assertThat(json("GET", "/liabilities/" + loan + "/transactions", null, owner, 200))
                .hasSize(22);
        assertThat(history(ORIGIN.minusDays(12), ORIGIN.plusMonths(1))).isEqualTo(oldHistory);
        assertThat(json("GET", "/accounts/" + cash, null, owner, 200).path("ownBalance"))
                .isEqualTo(oldCash.path("ownBalance"));
        for (JsonNode original : originals) {
            JsonNode linked =
                    json("GET", "/transactions/" + original.path("id").asLong(), null, owner, 200);
            for (String field :
                    List.of(
                            "date",
                            "amount",
                            "accountId",
                            "categoryId",
                            "tags",
                            "type",
                            "currency"))
                assertThat(linked.path(field)).as(field).isEqualTo(original.path(field));
            money(linked, "principalAmount", "0");
        }
        json("POST", "/history/" + action + "/undo", null, owner, 200);
        assertThat(
                        json("GET", "/accounts/" + source, null, owner, 200)
                                .path("isActive")
                                .asBoolean())
                .isTrue();
        assertThat(
                        json("GET", "/real-estate/" + property, null, owner, 200)
                                .path("mortgageId")
                                .isNull())
                .isTrue();
        assertThat(candidates(source).path("items")).hasSize(17);
        json("POST", "/history/" + action + "/redo", null, owner, 200);
        assertMortgageTotals(loan);
        assertThat(json("GET", "/liabilities/" + loan + "/transactions", null, owner, 200))
                .hasSize(22);
    }

    @Test
    void rejectsStalePaymentReviewAndForeignPaymentsWithoutPartialConversion() throws Exception {
        long source = source();
        long cash = cash(owner);
        Map<String, Object> expense = expense(cash, "35.83", ORIGIN.minusDays(12));
        expense.put("categoryId", category("Assurance du Prêt"));
        long payment = json("POST", "/transactions", expense, owner, 201).path("id").asLong();
        Map<String, Object> stale = request(source, candidates(source));
        expense.put("amount", "36.83");
        json("PUT", "/transactions/" + payment, expense, owner, 200);
        json("POST", "/liabilities/conversion", stale, owner, 400);
        assertUnconverted(source);
        Auth other = register();
        json("GET", "/liabilities/conversion/" + source + "/payments", null, other, 404);
        long foreignCash = cash(other);
        long foreign =
                json("POST", "/transactions", expense(foreignCash, "12", ORIGIN), other, 201)
                        .path("id")
                        .asLong();
        Map<String, Object> request = request(source, candidates(source));
        request.put(
                "payments",
                List.of(
                        data(
                                "transactionId",
                                foreign,
                                "sourceRevision",
                                "a".repeat(64),
                                "component",
                                "INTEREST")));
        json("POST", "/liabilities/conversion", request, owner, 400);
        assertUnconverted(source);
    }

    @Test
    void rejectsForgedSplitIdsDuplicatePaymentsAndPrincipalThatIsNotInOpeningDebt()
            throws Exception {
        long source = source();
        long cash = cash(owner);
        Map<String, Object> expense = expense(cash, "100", LocalDate.now());
        expense.put(
                "splits",
                List.of(
                        data("amount", 80, "categoryId", category("Intérêts du Prêt")),
                        data("amount", 20, "categoryId", category("Assurance du Prêt"))));
        json("POST", "/transactions", expense, owner, 201);
        JsonNode candidate = candidates(source).path("items").get(0);
        Map<String, Object> selection = selection(candidate);
        Map<String, Object> request = request(source, candidates(source));
        request.put("payments", List.of(selection, selection));
        json("POST", "/liabilities/conversion", request, owner, 400);
        selection.put(
                "splits",
                List.of(
                        data("splitId", -1, "component", "INTEREST"),
                        data(
                                "splitId",
                                candidate.path("splits").get(1).path("splitId").asLong(),
                                "component",
                                "INSURANCE")));
        request.put("payments", List.of(selection));
        json("POST", "/liabilities/conversion", request, owner, 400);
        selection.put(
                "splits",
                List.of(
                        data(
                                "splitId",
                                candidate.path("splits").get(0).path("splitId").asLong(),
                                "component",
                                "PRINCIPAL"),
                        data(
                                "splitId",
                                candidate.path("splits").get(1).path("splitId").asLong(),
                                "component",
                                "INSURANCE")));
        json("POST", "/liabilities/conversion", request, owner, 400);
        assertUnconverted(source);
        request.put("payments", List.of(selection(candidate)));
        long loan =
                json("POST", "/liabilities/conversion", request, owner, 201).path("id").asLong();
        money(json("GET", "/liabilities/" + loan, null, owner, 200), "currentBalance", "192500");
        money(
                json("GET", "/liabilities/" + loan + "/breakdown", null, owner, 200),
                "interestPaid",
                "80");
    }

    private void assertMortgageTotals(long loan) throws Exception {
        JsonNode breakdown = json("GET", "/liabilities/" + loan + "/breakdown", null, owner, 200);
        money(breakdown, "currentBalance", "192500");
        money(breakdown, "principalPaid", "0");
        money(breakdown, "interestPaid", "5757.46");
        money(breakdown, "insurancePaid", "590.62");
        money(breakdown, "feesPaid", "1075");
        money(breakdown, "totalPaid", "7423.08");
    }

    private void assertPaymentHistory(long loan) throws Exception {
        String path = "/liabilities/" + loan;
        JsonNode forecast = json("GET", path + "/amortization", null, owner, 200);
        JsonNode paid = json("GET", path + "/amortization/history", null, owner, 200);
        assertThat(paid).hasSize(17);
        assertThat(paid.get(0).path("paymentDate").asText())
                .isEqualTo(ORIGIN.minusDays(12).toString());
        money(paid.get(0), "insurancePortion", "17.34");
        money(paid.get(0), "remainingBalance", "0");
        money(paid.get(1), "feePortion", "1075");
        money(paid.get(1), "interestPortion", "104.94");
        money(paid.get(1), "insurancePortion", "35.83");
        money(paid.get(1), "remainingBalance", "57500");
        money(paid.get(16), "remainingBalance", "192500");
        BigDecimal total = BigDecimal.ZERO;
        for (JsonNode payment : paid) {
            total = total.add(payment.path("paymentAmount").decimalValue());
            money(payment, "principalPortion", "0");
            assertThat(payment.path("transactionId").asLong()).isPositive();
        }
        assertThat(total).isEqualByComparingTo("7423.08");
        assertThat(json("GET", path + "/amortization", null, owner, 200)).isEqualTo(forecast);
        JsonNode movements = json("GET", path + "/transactions", null, owner, 200);
        assertThat(
                        java.util.stream.StreamSupport.stream(movements.spliterator(), false)
                                .filter(
                                        tx ->
                                                "DISBURSEMENT"
                                                        .equals(tx.path("movementType").asText()))
                                .count())
                .isEqualTo(5);
        Auth other = register();
        json("GET", path + "/amortization/history", null, other, 404);
    }

    private void assertUnconverted(long source) throws Exception {
        assertThat(json("GET", "/liabilities", null, owner, 200)).isEmpty();
        JsonNode account = json("GET", "/accounts/" + source, null, owner, 200);
        assertThat(account.path("isActive").asBoolean()).isTrue();
        money(account, "ownBalance", "-192500");
    }

    private long source() throws Exception {
        long id =
                json(
                                "POST",
                                "/accounts",
                                data(
                                        "name",
                                        "00000000001",
                                        "type",
                                        "OTHER",
                                        "currency",
                                        "EUR",
                                        "initialBalance",
                                        0,
                                        "openingDate",
                                        ORIGIN),
                                owner,
                                201)
                        .path("id")
                        .asLong();
        jdbc.update("UPDATE accounts SET import_source = 'SKROOGE_JSON' WHERE id = ?", id);
        String[] draws = {"57500", "11250", "33750", "33750", "56250"};
        for (int i = 0; i < draws.length; i++)
            json(
                    "POST",
                    "/transactions",
                    expense(id, draws[i], ORIGIN.plusMonths(i * 3L)),
                    owner,
                    201);
        return id;
    }

    private long cash(Auth user) throws Exception {
        return json(
                        "POST",
                        "/accounts",
                        data(
                                "name",
                                "00000000002",
                                "type",
                                "CHECKING",
                                "currency",
                                "EUR",
                                "initialBalance",
                                10000,
                                "openingDate",
                                ORIGIN.minusMonths(1)),
                        user,
                        201)
                .path("id")
                .asLong();
    }

    private long category(String name) throws Exception {
        return json("POST", "/categories", data("name", name), owner, 201).path("id").asLong();
    }

    private Map<String, Object> expense(long account, String amount, LocalDate date) {
        return data(
                "accountId",
                account,
                "type",
                "EXPENSE",
                "amount",
                amount,
                "currency",
                "EUR",
                "date",
                date,
                "description",
                "ECHEANCE PRET",
                "notes",
                "PRET 00000 00000001");
    }

    private void createMortgagePayments(long cash) throws Exception {
        long interest = category("Intérêts du Prêt");
        long insurance = category("Assurance du Prêt");
        long fee = category("Frais de dossier");
        Map<String, Object> first = expense(cash, "17.34", ORIGIN.minusDays(12));
        first.putAll(data("categoryId", insurance, "tags", "Property"));
        json("POST", "/transactions", first, owner, 201);
        String[] interests = {
            "104.94", "198.08", "209.11", "209.11", "308.46", "311.77", "311.77", "311.77",
            "366.77", "414.43", "414.43", "414.43", "425.83", "585.52", "585.52", "585.52"
        };
        for (int i = 0; i < interests.length; i++) {
            BigDecimal amount = new BigDecimal(interests[i]).add(new BigDecimal("35.83"));
            List<Map<String, Object>> parts =
                    new ArrayList<>(
                            List.of(
                                    data("amount", interests[i], "categoryId", interest),
                                    data("amount", "35.83", "categoryId", insurance)));
            if (i == 0) {
                amount = amount.add(new BigDecimal("1075"));
                parts.add(data("amount", "1075", "categoryId", fee));
            }
            Map<String, Object> payment =
                    expense(
                            cash,
                            amount.toPlainString(),
                            ORIGIN.plusMonths(i + 1L).withDayOfMonth(5));
            payment.put("splits", parts);
            json("POST", "/transactions", payment, owner, 201);
        }
    }

    private JsonNode candidates(long source) throws Exception {
        return json("GET", "/liabilities/conversion/" + source + "/payments", null, owner, 200);
    }

    private Map<String, Object> selection(JsonNode candidate) {
        List<Map<String, Object>> parts = new ArrayList<>();
        for (JsonNode part : candidate.path("splits"))
            parts.add(
                    data(
                            "splitId",
                            part.path("splitId").asLong(),
                            "component",
                            part.path("suggestedComponent").asText()));
        return data(
                "transactionId",
                candidate.path("transactionId").asLong(),
                "sourceRevision",
                candidate.path("sourceRevision").asText(),
                "component",
                candidate.path("splits").isEmpty()
                        ? candidate.path("suggestedComponent").asText()
                        : null,
                "splits",
                parts);
    }

    private Map<String, Object> request(long source, JsonNode payments) throws Exception {
        JsonNode candidate =
                json("GET", "/liabilities/conversion/candidates", null, owner, 200).get(0);
        List<Map<String, Object>> selections = new ArrayList<>();
        for (JsonNode payment : payments.path("items")) selections.add(selection(payment));
        return data(
                "operationId",
                UUID.randomUUID(),
                "accountId",
                source,
                "sourceRevision",
                candidate.path("sourceRevision").asText(),
                "fundedPrincipal",
                "192500",
                "payments",
                selections,
                "liability",
                data(
                        "name",
                        "00000000001",
                        "type",
                        "MORTGAGE",
                        "principal",
                        "215000",
                        "currentBalance",
                        "192500",
                        "currency",
                        "EUR",
                        "startDate",
                        ORIGIN));
    }
}
