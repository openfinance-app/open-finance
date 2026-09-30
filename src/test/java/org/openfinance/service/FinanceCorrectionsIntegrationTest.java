package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Persistence and accounting regressions complementing the real browser purchase/edit flows. */
class FinanceCorrectionsIntegrationTest extends AuditApiTestSupport {
    private Map<String, Object> mortgage(String currency, String principal, String balance) {
        return data(
                "name",
                "Correction mortgage",
                "type",
                "MORTGAGE",
                "principal",
                principal,
                "currentBalance",
                balance,
                "currency",
                currency,
                "startDate",
                START.toString(),
                "interestRate",
                0);
    }

    private Map<String, Object> propertyData() {
        return data(
                "name",
                "Correction home",
                "address",
                "Synthetic correction address",
                "propertyType",
                "RESIDENTIAL",
                "purchasePrice",
                "100000",
                "currentValue",
                "100000",
                "purchaseDate",
                START.toString(),
                "currency",
                "EUR",
                "isActive",
                true);
    }

    private Map<String, Object> purchase(long cash) {
        return data(
                "operationId",
                UUID.randomUUID().toString(),
                "property",
                propertyData(),
                "newMortgage",
                mortgage("EUR", "80000", "0"),
                "loanAmount",
                "80000",
                "downPayment",
                "20000",
                "route",
                "ACCOUNT",
                "accountId",
                cash,
                "paymentDescription",
                "Correction purchase");
    }

    @Test
    void aCommittedPurchaseCanBeRetriedWithoutRepeatingAnyMovement() throws Exception {
        long cash = account(owner);
        Map<String, Object> request = purchase(cash);
        JsonNode first = json("POST", "/real-estate/purchase", request, owner, 201);
        JsonNode retry = json("POST", "/real-estate/purchase", request, owner, 201);
        assertThat(retry).isEqualTo(first);
        money(json("GET", "/accounts/" + cash, null, owner, 200), "ownBalance", "-19000");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM real_estate_properties WHERE user_id = ?",
                                Integer.class,
                                owner.id()))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM liabilities WHERE user_id = ?",
                                Integer.class,
                                owner.id()))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM transactions WHERE user_id = ?",
                                Integer.class,
                                owner.id()))
                .isEqualTo(2);
        request.put("paymentDescription", "Different purchase");
        json("POST", "/real-estate/purchase", request, owner, 400);
        money(json("GET", "/accounts/" + cash, null, owner, 200), "ownBalance", "-19000");
    }

    @Test
    void aFailedDrawRollsBackThePropertyAndLeavesNoRetryReceipt() throws Exception {
        long cash = account(owner);
        Map<String, Object> loan = mortgage("EUR", "80000", "0");
        loan.put("startDate", LocalDate.now().toString());
        long loanId = json("POST", "/liabilities", loan, owner, 201).path("id").asLong();
        Map<String, Object> request = purchase(cash);
        request.remove("newMortgage");
        request.put("existingMortgageId", loanId);
        json("POST", "/real-estate/purchase", request, owner, 400);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM real_estate_properties WHERE user_id = ?",
                                Integer.class,
                                owner.id()))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM property_purchase_receipts WHERE user_id = ?",
                                Integer.class,
                                owner.id()))
                .isZero();
        money(json("GET", "/accounts/" + cash, null, owner, 200), "ownBalance", "1000");
    }

    @Test
    void linkedMortgageDeletionUnlinksActiveAndInactivePropertiesAndUndoRestoresTheLink()
            throws Exception {
        for (boolean active : new boolean[] {true, false}) {
            long mortgage =
                    json("POST", "/liabilities", mortgage("EUR", "80000", "80000"), owner, 201)
                            .path("id")
                            .asLong();
            Map<String, Object> property = propertyData();
            property.put("mortgageId", mortgage);
            property.put("isActive", active);
            long propertyId =
                    json("POST", "/real-estate", property, owner, 201).path("id").asLong();
            json("DELETE", "/liabilities/" + mortgage, null, owner, 204);
            assertThat(
                            json("GET", "/real-estate/" + propertyId, null, owner, 200)
                                    .path("mortgageId")
                                    .isNull())
                    .isTrue();
            long action =
                    jdbc.queryForObject(
                            "SELECT MAX(id) FROM operation_history WHERE user_id = ?",
                            Long.class,
                            owner.id());
            json("POST", "/history/" + action + "/undo", null, owner, 200);
            assertThat(
                            json("GET", "/real-estate/" + propertyId, null, owner, 200)
                                    .path("mortgageId")
                                    .asLong())
                    .isEqualTo(mortgage);
        }
    }

    @Test
    void inactivePropertyRetainsItsEarlierValueAndReactivationIsDated() throws Exception {
        Map<String, Object> property = propertyData();
        long id = json("POST", "/real-estate", property, owner, 201).path("id").asLong();
        money(history(START, START).get(0), "netWorth", "100000");
        property.put("currentValue", "120000");
        property.put("isActive", false);
        json("PUT", "/real-estate/" + id, property, owner, 200);
        money(history(START, START).get(0), "netWorth", "100000");
        money(history(LocalDate.now(), LocalDate.now()).get(0), "netWorth", "0");
        property.put("isActive", true);
        json("PUT", "/real-estate/" + id, property, owner, 200);
        money(history(START, START).get(0), "netWorth", "100000");
        money(history(LocalDate.now(), LocalDate.now()).get(0), "netWorth", "120000");
        json("DELETE", "/real-estate/" + id, null, owner, 204);
        money(history(START, START).get(0), "netWorth", "100000");
    }

    @Test
    void allocationIncludesStandaloneDebtAfterJpaDecryption() throws Exception {
        account(owner);
        loan(START);
        JsonNode allocations = json("GET", "/dashboard/networth-allocation", null, owner, 200);
        BigDecimal sum = BigDecimal.ZERO;
        BigDecimal debt = BigDecimal.ZERO;
        for (JsonNode allocation : allocations) {
            BigDecimal value = allocation.path("value").decimalValue();
            sum = sum.add(value);
            if (value.signum() < 0) debt = debt.add(value);
        }
        assertThat(sum).isEqualByComparingTo("0");
        assertThat(debt).isEqualByComparingTo("-1000");
    }

    @Test
    void cryptoConversionRejectsAWholeFiatCentTolerance() throws Exception {
        long cash =
                json(
                                "POST",
                                "/accounts",
                                data(
                                        "name",
                                        "Bitcoin",
                                        "type",
                                        "CHECKING",
                                        "currency",
                                        "BTC",
                                        "initialBalance",
                                        "1"),
                                owner,
                                201)
                        .path("id")
                        .asLong();
        Map<String, Object> tx =
                data(
                        "accountId",
                        cash,
                        "type",
                        "EXPENSE",
                        "amount",
                        "0.005",
                        "currency",
                        "BTC",
                        "date",
                        LocalDate.now().toString(),
                        "originalAmount",
                        "100",
                        "originalCurrency",
                        "EUR",
                        "conversionRate",
                        "0.00001");
        json("POST", "/transactions", tx, owner, 400);
        tx.put("amount", "0.001");
        json("POST", "/transactions", tx, owner, 201);
        money(json("GET", "/accounts/" + cash, null, owner, 200), "ownBalance", "0.999");
    }

    @Test
    void smallDrawsAndRecurringAmountsKeepTheirPrecisionAndOmittedPatchFieldsStayUnchanged()
            throws Exception {
        for (String currency : new String[] {"BTC", "KWD"}) {
            String amount = currency.equals("BTC") ? "0.001000000000000001" : "1.234";
            long cash =
                    json(
                                    "POST",
                                    "/accounts",
                                    data(
                                            "name",
                                            currency,
                                            "type",
                                            "CHECKING",
                                            "currency",
                                            currency,
                                            "initialBalance",
                                            "0"),
                                    owner,
                                    201)
                            .path("id")
                            .asLong();
            long loan =
                    json("POST", "/liabilities", mortgage(currency, "2", "0"), owner, 201)
                            .path("id")
                            .asLong();
            long tranche =
                    json(
                                    "POST",
                                    "/liabilities/" + loan + "/tranches",
                                    data("plannedAmount", amount, "interestOnly", true),
                                    owner,
                                    201)
                            .path("id")
                            .asLong();
            assertThat(
                            json(
                                            "PATCH",
                                            "/tranches/" + tranche,
                                            data("notes", "Planned notes"),
                                            owner,
                                            200)
                                    .path("interestOnly")
                                    .asBoolean())
                    .isTrue();
            json(
                    "POST",
                    "/liabilities/" + loan + "/disburse",
                    data(
                            "amount",
                            amount,
                            "date",
                            LocalDate.now().toString(),
                            "toAccountId",
                            cash,
                            "trancheId",
                            tranche),
                    owner,
                    200);
            money(json("GET", "/accounts/" + cash, null, owner, 200), "ownBalance", amount);
            assertThat(
                            json(
                                            "PATCH",
                                            "/tranches/" + tranche,
                                            data("notes", "Drawn notes"),
                                            owner,
                                            200)
                                    .path("interestOnly")
                                    .asBoolean())
                    .isTrue();
            JsonNode recurring =
                    json(
                            "POST",
                            "/recurring-transactions",
                            data(
                                    "description",
                                    "Small recurring",
                                    "accountId",
                                    cash,
                                    "type",
                                    "EXPENSE",
                                    "amount",
                                    amount,
                                    "currency",
                                    currency,
                                    "frequency",
                                    "MONTHLY",
                                    "nextOccurrence",
                                    LocalDate.now().plusDays(1).toString()),
                            owner,
                            201);
            money(recurring, "amount", amount);
        }
    }

    @Test
    void interestRoundsOnlyAtTheMoneyBoundaryAndInsuranceStopsAtPayoff() throws Exception {
        Map<String, Object> loan = mortgage("EUR", "300000", "300000");
        loan.put("interestRate", "4.1");
        money(json("POST", "/liabilities", loan, owner, 201), "monthlyInterestCost", "1025.00");
        loan = mortgage("EUR", "10000", "10000");
        loan.putAll(
                data(
                        "minimumPayment",
                        "10000",
                        "insurancePercentage",
                        "1.2",
                        "endDate",
                        LocalDate.now().plusYears(1).toString()));
        long id = json("POST", "/liabilities", loan, owner, 201).path("id").asLong();
        money(
                json("GET", "/liabilities/" + id + "/breakdown", null, owner, 200),
                "projectedInsurance",
                "10");
        long cash = account(owner);
        Map<String, Object> payment = movement(cash, 10010, LocalDate.now());
        payment.putAll(
                data("liabilityId", id, "movementType", "REPAYMENT", "principalAmount", "10000"));
        json("POST", "/transactions", payment, owner, 201);
        money(
                json("GET", "/liabilities/" + id + "/breakdown", null, owner, 200),
                "projectedInsurance",
                "0");
        money(json("GET", "/liabilities/" + id, null, owner, 200), "effectiveMonthlyPayment", "0");
    }
}
