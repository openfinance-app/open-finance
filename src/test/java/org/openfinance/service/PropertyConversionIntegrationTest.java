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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openfinance.entity.AssetPriceHistory;
import org.openfinance.repository.AssetPriceHistoryRepository;
import org.openfinance.security.EncryptionContext;
import org.openfinance.security.EncryptionKeyCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/** Real encrypted conversion, history, authorization and retry semantics on SQLite/PostgreSQL. */
@TestPropertySource(properties = "application.import.skrooge-json-enabled=true")
class PropertyConversionIntegrationTest extends AuditApiTestSupport {
    @Autowired AssetPriceHistoryRepository prices;
    @Autowired EncryptionKeyCache keys;
    @Autowired AccountService accounts;

    @BeforeEach
    void euroReporting() {
        jdbc.update("UPDATE users SET base_currency = 'EUR' WHERE id = ?", owner.id());
    }

    @Test
    void conversionReusesAssetAndPreservesCashAndDatedNetWorth() throws Exception {
        Fixture fixture = fixture();
        List<LocalDate> dates =
                List.of(START.plusDays(5), START.plusDays(15), START.plusDays(25), LocalDate.now());
        List<BigDecimal> before = new java.util.ArrayList<>();
        for (LocalDate date : dates)
            before.add(history(date, date).get(0).path("netWorth").decimalValue());
        long propertyId = convert(fixture);
        JsonNode property = json("GET", "/real-estate/" + propertyId, null, owner, 200);
        assertThat(property.path("assetId").asLong()).isEqualTo(fixture.asset());
        assertThat(property.path("convertedFromHolding").asBoolean()).isTrue();
        money(property, "purchasePrice", "1500");
        money(property, "currentValue", "1800");
        money(json("GET", "/accounts/" + fixture.account(), null, owner, 200), "ownBalance", "0");
        assertThat(
                        json("GET", "/accounts/" + fixture.account(), null, owner, 200)
                                .path("isActive")
                                .asBoolean())
                .isFalse();
        assertThat(count("assets")).isEqualTo(1);
        assertThat(count("transactions")).isEqualTo(1);
        JsonNode asset = json("GET", "/assets/" + fixture.asset(), null, owner, 200);
        assertThat(asset.path("type").asText()).isEqualTo("REAL_ESTATE");
        assertThat(asset.path("investmentTracked").asBoolean()).isFalse();
        money(asset, "totalValue", "1800");
        for (int i = 0; i < dates.size(); i++)
            assertThat(history(dates.get(i), dates.get(i)).get(0).path("netWorth").decimalValue())
                    .isEqualByComparingTo(before.get(i));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT address FROM real_estate_properties WHERE id = ?",
                                String.class,
                                propertyId))
                .doesNotContain("Synthetic street");
        assertThat(json("GET", "/real-estate/conversion/candidates", null, owner, 200)).isEmpty();
    }

    @Test
    void retriesAreIdempotentAndUndoRestoresTheOriginalHolding() throws Exception {
        Fixture fixture = fixture();
        Map<String, Object> request = request(fixture);
        long property =
                json("POST", "/real-estate/conversion", request, owner, 201)
                        .path("propertyId")
                        .asLong();
        long action =
                jdbc.queryForObject(
                        "SELECT MAX(id) FROM operation_history WHERE user_id = ?",
                        Long.class,
                        owner.id());
        assertThat(
                        json("POST", "/real-estate/conversion", request, owner, 201)
                                .path("propertyId")
                                .asLong())
                .isEqualTo(property);
        assertThat(count("real_estate_properties")).isEqualTo(1);
        Map<String, Object> conflicting = new java.util.LinkedHashMap<>(request);
        conflicting.put("name", "Different property");
        json("POST", "/real-estate/conversion", conflicting, owner, 400);
        conflicting.put("operationId", UUID.randomUUID().toString());
        json("POST", "/real-estate/conversion", conflicting, owner, 400);
        json("POST", "/history/" + action + "/undo", null, owner, 200);
        JsonNode asset = json("GET", "/assets/" + fixture.asset(), null, owner, 200);
        assertThat(asset.path("type").asText()).isEqualTo("STOCK");
        money(asset, "quantity", "1.5");
        assertThat(count("real_estate_properties")).isZero();
        assertThat(
                        json("GET", "/accounts/" + fixture.account(), null, owner, 200)
                                .path("isActive")
                                .asBoolean())
                .isTrue();
        json("POST", "/real-estate/conversion", request, owner, 400);
        json("POST", "/history/" + action + "/redo", null, owner, 200);
        assertThat(
                        json("GET", "/accounts/" + fixture.account(), null, owner, 200)
                                .path("isActive")
                                .asBoolean())
                .isFalse();
        assertThat(
                        json("GET", "/real-estate/" + property, null, owner, 200)
                                .path("assetId")
                                .asLong())
                .isEqualTo(fixture.asset());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void sourceAccountWithCashOrAnotherHoldingStaysOpen(boolean hasCash) throws Exception {
        Fixture fixture = fixture();
        if (hasCash) {
            json(
                    "POST",
                    "/transactions",
                    data(
                            "accountId",
                            fixture.account(),
                            "type",
                            "INCOME",
                            "currency",
                            "EUR",
                            "amount",
                            "25",
                            "date",
                            START.toString()),
                    owner,
                    201);
        } else {
            json(
                    "POST",
                    "/assets",
                    data(
                            "name",
                            "Another holding",
                            "type",
                            "STOCK",
                            "accountId",
                            fixture.account(),
                            "quantity",
                            "1",
                            "purchasePrice",
                            "100",
                            "currentPrice",
                            "100",
                            "purchaseDate",
                            START.toString(),
                            "currency",
                            "EUR"),
                    owner,
                    201);
        }
        assertThat(
                        json("GET", "/real-estate/conversion/candidates", null, owner, 200)
                                .get(0)
                                .path("closesSourceAccount")
                                .asBoolean())
                .isFalse();
        convert(fixture);
        assertThat(
                        json("GET", "/accounts/" + fixture.account(), null, owner, 200)
                                .path("isActive")
                                .asBoolean())
                .isTrue();
    }

    @Test
    void sourceAccountChangesRequireReviewingConversionAgain() throws Exception {
        Fixture fixture = fixture();
        assertThat(
                        json("GET", "/real-estate/conversion/candidates", null, owner, 200)
                                .get(0)
                                .path("closesSourceAccount")
                                .asBoolean())
                .isTrue();
        Map<String, Object> request = request(fixture);
        json(
                "POST",
                "/transactions",
                data(
                        "accountId",
                        fixture.account(),
                        "type",
                        "INCOME",
                        "currency",
                        "EUR",
                        "amount",
                        "25",
                        "date",
                        START.toString()),
                owner,
                201);
        json("POST", "/real-estate/conversion", request, owner, 400);
        assertThat(count("real_estate_properties")).isZero();
        assertThat(
                        json("GET", "/accounts/" + fixture.account(), null, owner, 200)
                                .path("isActive")
                                .asBoolean())
                .isTrue();
    }

    @Test
    void propertyActivityIncludesOriginalHistoryAndNewCostsWithoutReclassifyingThem()
            throws Exception {
        Fixture fixture = fixture();
        long property = convert(fixture);
        long payer =
                json(
                                "POST",
                                "/accounts",
                                data(
                                        "name",
                                        "Payment account",
                                        "type",
                                        "CHECKING",
                                        "currency",
                                        "EUR",
                                        "initialBalance",
                                        "100",
                                        "openingDate",
                                        START.toString()),
                                owner,
                                201)
                        .path("id")
                        .asLong();
        long cost =
                json(
                                "POST",
                                "/transactions",
                                data(
                                        "accountId",
                                        payer,
                                        "type",
                                        "EXPENSE",
                                        "currency",
                                        "EUR",
                                        "amount",
                                        "25",
                                        "date",
                                        LocalDate.now().toString(),
                                        "realEstateId",
                                        property,
                                        "movementType",
                                        "MAINTENANCE"),
                                owner,
                                201)
                        .path("id")
                        .asLong();
        // An unrelated payment must not appear in the property's list or summary.
        json(
                "POST",
                "/transactions",
                data(
                        "accountId",
                        payer,
                        "type",
                        "EXPENSE",
                        "currency",
                        "EUR",
                        "amount",
                        "10",
                        "date",
                        LocalDate.now().toString()),
                owner,
                201);
        String filter = "realEstateId=" + property;
        JsonNode first =
                json(
                        "GET",
                        "/transactions/search?" + filter + "&size=1&sort=date,asc",
                        null,
                        owner,
                        200);
        assertThat(first.path("totalElements").asInt()).isEqualTo(2);
        JsonNode original = first.path("content").get(0);
        assertThat(original.path("id").asLong()).isEqualTo(fixture.transaction());
        assertThat(original.path("assetId").asLong()).isEqualTo(fixture.asset());
        assertThat(original.path("realEstateId").isNull()).isTrue();
        assertThat(original.path("type").asText()).isEqualTo("INCOME");
        money(original, "amount", "500");
        JsonNode second =
                json(
                        "GET",
                        "/transactions/search?" + filter + "&size=1&page=1&sort=date,asc",
                        null,
                        owner,
                        200);
        assertThat(second.path("content").get(0).path("id").asLong()).isEqualTo(cost);
        JsonNode expenses =
                json("GET", "/transactions/search?" + filter + "&type=EXPENSE", null, owner, 200);
        assertThat(expenses.path("totalElements").asInt()).isEqualTo(1);
        assertThat(expenses.path("content").get(0).path("id").asLong()).isEqualTo(cost);
        JsonNode summary = json("GET", "/transactions/summary?" + filter, null, owner, 200);
        assertThat(summary.path("transactionCount").asInt()).isEqualTo(2);
        money(summary, "incomeAmount", "500");
        money(summary, "expenseAmount", "25");
        Auth other = register();
        assertThat(
                        json("GET", "/transactions/search?" + filter, null, other, 200)
                                .path("totalElements")
                                .asInt())
                .isZero();
        money(json("GET", "/real-estate/" + property, null, owner, 200), "purchasePrice", "1500");
    }

    @Test
    void partialGiftKeepsItsRemainingContributionAsPropertyBasis() throws Exception {
        long account =
                json(
                                "POST",
                                "/accounts",
                                data(
                                        "name",
                                        "Gift account",
                                        "type",
                                        "INVESTMENT",
                                        "currency",
                                        "EUR",
                                        "initialBalance",
                                        "0",
                                        "openingDate",
                                        START.toString()),
                                owner,
                                201)
                        .path("id")
                        .asLong();
        long asset =
                json(
                                "POST",
                                "/assets",
                                data(
                                        "name",
                                        "Gifted apartment unit",
                                        "type",
                                        "STOCK",
                                        "accountId",
                                        account,
                                        "quantity",
                                        "1",
                                        "purchasePrice",
                                        "0",
                                        "currentPrice",
                                        "1200",
                                        "purchaseDate",
                                        START.toString(),
                                        "currency",
                                        "EUR",
                                        "acquisitionType",
                                        "GIFT",
                                        "receivedValue",
                                        "1000"),
                                owner,
                                201)
                        .path("id")
                        .asLong();
        long transaction =
                json(
                                "POST",
                                "/transactions",
                                data(
                                        "accountId",
                                        account,
                                        "type",
                                        "EXPENSE",
                                        "currency",
                                        "EUR",
                                        "amount",
                                        "600",
                                        "date",
                                        START.plusDays(20).toString(),
                                        "description",
                                        "Partial transfer",
                                        "investment",
                                        data(
                                                "assetId",
                                                asset,
                                                "operation",
                                                "REMOVE",
                                                "pricing",
                                                "QUANTITY",
                                                "quantity",
                                                "0.5")),
                                owner,
                                201)
                        .path("id")
                        .asLong();
        jdbc.update("UPDATE assets SET import_source = ? WHERE id = ?", "skrooge:unit:15", asset);
        long property = convert(new Fixture(account, asset, transaction));
        JsonNode result = json("GET", "/real-estate/" + property, null, owner, 200);
        money(result, "purchasePrice", "0");
        money(result, "receivedValue", "500");
        money(result, "currentValue", "600");
        money(result, "appreciation", "100");
        money(json("GET", "/assets/" + asset, null, owner, 200), "receivedValue", "500");
    }

    @Test
    void staleSourceAndForeignOwnerCannotConvert() throws Exception {
        Fixture fixture = fixture();
        Map<String, Object> request = request(fixture);
        Auth other = register();
        assertThat(json("GET", "/real-estate/conversion/candidates", null, other, 200)).isEmpty();
        json("POST", "/real-estate/conversion", request, other, 404);
        json(
                "PUT",
                "/assets/" + fixture.asset(),
                data(
                        "name",
                        "Imported apartment unit",
                        "type",
                        "STOCK",
                        "accountId",
                        fixture.account(),
                        "quantity",
                        "1.5",
                        "purchasePrice",
                        "1000",
                        "currentPrice",
                        "1250",
                        "purchaseDate",
                        START.toString(),
                        "currency",
                        "EUR"),
                owner,
                200);
        json("POST", "/real-estate/conversion", request, owner, 400);
        assertThat(count("real_estate_properties")).isZero();
        assertThat(
                        json("GET", "/assets/" + fixture.asset(), null, owner, 200)
                                .path("type")
                                .asText())
                .isEqualTo("STOCK");
    }

    @Test
    void rejectedMortgageRollsBackWithoutTouchingTheSource() throws Exception {
        Fixture fixture = fixture();
        Auth other = register();
        Map<String, Object> request = request(fixture);
        request.put("mortgageId", loanFor(other));
        json("POST", "/real-estate/conversion", request, owner, 404);
        assertThat(count("real_estate_properties")).isZero();
        assertThat(count("property_conversion_receipts")).isZero();
        money(json("GET", "/assets/" + fixture.asset(), null, owner, 200), "quantity", "1.5");
    }

    @Test
    void propertyCanBeManagedWhileOriginalInvestmentMovementIsProtected() throws Exception {
        Fixture fixture = fixture();
        long property = convert(fixture);
        Map<String, Object> propertyEdit =
                data(
                        "name",
                        "Apartment renamed",
                        "address",
                        "Synthetic street",
                        "propertyType",
                        "RESIDENTIAL",
                        "purchasePrice",
                        "1500",
                        "currentValue",
                        "1900",
                        "purchaseDate",
                        START.toString(),
                        "currency",
                        "EUR",
                        "isActive",
                        true);
        json("PUT", "/real-estate/" + property, propertyEdit, owner, 200);
        money(json("GET", "/assets/" + fixture.asset(), null, owner, 200), "totalValue", "1900");
        EncryptionContext.setKey(keys.getKeyBySessionToken(owner.session()).orElseThrow());
        try {
            accounts.recalculateBalance(fixture.account(), owner.id());
        } finally {
            EncryptionContext.clear();
        }
        money(json("GET", "/assets/" + fixture.asset(), null, owner, 200), "totalValue", "1900");
        Map<String, Object> tx = investment(fixture.account(), fixture.asset());
        tx.put("description", "Metadata correction");
        json("PUT", "/transactions/" + fixture.transaction(), tx, owner, 200);
        tx.put("amount", "600");
        json("PUT", "/transactions/" + fixture.transaction(), tx, owner, 400);
        json("DELETE", "/transactions/" + fixture.transaction(), null, owner, 400);
        money(json("GET", "/assets/" + fixture.asset(), null, owner, 200), "totalValue", "1900");
        json("DELETE", "/real-estate/" + property, null, owner, 204);
        assertThat(count("assets")).isEqualTo(1);
        money(history(START.plusDays(25), START.plusDays(25)).get(0), "netWorth", "1650");
        money(history(LocalDate.now(), LocalDate.now()).get(0), "netWorth", "0");
    }

    private Fixture fixture() throws Exception {
        long account =
                json(
                                "POST",
                                "/accounts",
                                data(
                                        "name",
                                        "Apartment account",
                                        "type",
                                        "INVESTMENT",
                                        "currency",
                                        "EUR",
                                        "initialBalance",
                                        "0",
                                        "openingDate",
                                        START.toString()),
                                owner,
                                201)
                        .path("id")
                        .asLong();
        long asset =
                json(
                                "POST",
                                "/assets",
                                data(
                                        "name",
                                        "Imported apartment unit",
                                        "type",
                                        "STOCK",
                                        "accountId",
                                        account,
                                        "quantity",
                                        "1",
                                        "purchasePrice",
                                        "1000",
                                        "currentPrice",
                                        "1200",
                                        "purchaseDate",
                                        START.toString(),
                                        "currency",
                                        "EUR"),
                                owner,
                                201)
                        .path("id")
                        .asLong();
        long transaction =
                json("POST", "/transactions", investment(account, asset), owner, 201)
                        .path("id")
                        .asLong();
        jdbc.update("UPDATE assets SET import_source = ? WHERE id = ?", "skrooge:unit:15", asset);
        EncryptionContext.setKey(keys.getKeyBySessionToken(owner.session()).orElseThrow());
        try {
            for (int offset : new int[] {0, 10, 30})
                prices.save(
                        AssetPriceHistory.builder()
                                .assetId(asset)
                                .userId(owner.id())
                                .priceDate(START.plusDays(offset))
                                .price(
                                        new BigDecimal(
                                                offset == 0
                                                        ? "1000"
                                                        : offset == 10 ? "1100" : "1200"))
                                .currency("EUR")
                                .source("SKROOGE")
                                .build());
        } finally {
            EncryptionContext.clear();
        }
        return new Fixture(account, asset, transaction);
    }

    private Map<String, Object> investment(long account, long asset) {
        return data(
                "accountId",
                account,
                "type",
                "INCOME",
                "currency",
                "EUR",
                "amount",
                "500",
                "date",
                START.plusDays(20).toString(),
                "description",
                "Imported property contribution",
                "investment",
                data(
                        "assetId",
                        asset,
                        "operation",
                        "ADD",
                        "pricing",
                        "QUANTITY",
                        "quantity",
                        "0.5"));
    }

    private Map<String, Object> request(Fixture fixture) throws Exception {
        JsonNode candidate =
                json("GET", "/real-estate/conversion/candidates", null, owner, 200).get(0);
        return data(
                "operationId",
                UUID.randomUUID().toString(),
                "assetId",
                fixture.asset(),
                "sourceRevision",
                candidate.path("sourceRevision").asText(),
                "name",
                "My apartment",
                "address",
                "Synthetic street",
                "propertyType",
                "RESIDENTIAL");
    }

    private long convert(Fixture fixture) throws Exception {
        return json("POST", "/real-estate/conversion", request(fixture), owner, 201)
                .path("propertyId")
                .asLong();
    }

    private long loanFor(Auth auth) throws Exception {
        return json(
                        "POST",
                        "/liabilities",
                        data(
                                "name",
                                "Mortgage",
                                "type",
                                "MORTGAGE",
                                "principal",
                                "1000",
                                "currentBalance",
                                "1000",
                                "currency",
                                "EUR",
                                "startDate",
                                START.toString()),
                        auth,
                        201)
                .path("id")
                .asLong();
    }

    private long count(String table) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE user_id = ?", Long.class, owner.id());
    }

    private record Fixture(long account, long asset, long transaction) {}
}
