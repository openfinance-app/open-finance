package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openfinance.security.EncryptionKeyCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Real encrypted database regressions for defects found in the September implementation audit. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class DomainManagementIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DataSource dataSource;
    @Autowired private EncryptionKeyCache keys;
    @TempDir Path temporary;
    private Auth owner;
    private Auth other;
    private static final String MASTER = "SourceMaster123!";

    @org.springframework.test.context.DynamicPropertySource
    static void useWalForSqlite(org.springframework.test.context.DynamicPropertyRegistry registry)
            throws Exception {
        String configured = System.getenv("SPRING_DATASOURCE_URL");
        if (configured == null || !configured.startsWith("jdbc:postgresql:")) {
            Files.createDirectories(Path.of("target", "test-dbs"));
            Path database =
                    Files.createTempFile(Path.of("target", "test-dbs"), "domain-wal-", ".db");
            registry.add(
                    "spring.datasource.url",
                    () ->
                            "jdbc:sqlite:"
                                    + database
                                    + "?foreign_keys=on&journal_mode=WAL&busy_timeout=10000");
        }
    }

    private record Auth(long id, String username, String token, String session) {}

    @BeforeEach
    void registerOwners() throws Exception {
        owner = register("source", MASTER);
    }

    private Auth register(String prefix, String master) throws Exception {
        String name = prefix + UUID.randomUUID().toString().substring(0, 8);
        json(
                "POST",
                "/auth/register",
                Map.of(
                        "username",
                        name,
                        "email",
                        name + "@example.invalid",
                        "password",
                        "LoginPassword123!",
                        "masterPassword",
                        master,
                        "skipSeeding",
                        true),
                null,
                201);
        JsonNode auth =
                json(
                        "POST",
                        "/auth/login",
                        Map.of(
                                "username",
                                name,
                                "password",
                                "LoginPassword123!",
                                "masterPassword",
                                master),
                        null,
                        200);
        long id = jdbc.queryForObject("SELECT id FROM users WHERE username = ?", Long.class, name);
        return new Auth(id, name, auth.get("token").asText(), auth.get("encryptionKey").asText());
    }

    private MockHttpServletRequestBuilder authenticated(
            MockHttpServletRequestBuilder request, Auth auth) {
        return auth == null
                ? request
                : request.header("Authorization", "Bearer " + auth.token())
                        .header("X-Encryption-Session", auth.session());
    }

    private JsonNode json(String method, String path, Object body, Auth auth, int expected)
            throws Exception {
        MockHttpServletRequestBuilder request =
                request(HttpMethod.valueOf(method), "/api/v1" + path);
        if (body != null)
            request.contentType(MediaType.APPLICATION_JSON)
                    .content(
                            mapper.copy()
                                    .disable(
                                            com.fasterxml.jackson.databind.SerializationFeature
                                                    .WRITE_DATES_AS_TIMESTAMPS)
                                    .writeValueAsBytes(body));
        org.springframework.mock.web.MockHttpServletResponse response =
                mvc.perform(authenticated(request, auth)).andReturn().getResponse();
        String result = response.getContentAsString();
        assertThat(response.getStatus()).as("%s %s: %s", method, path, result).isEqualTo(expected);
        return result.isBlank() ? mapper.nullNode() : mapper.readTree(result);
    }

    private long account(Auth auth, String name, String currency, int balance) throws Exception {
        return json(
                        "POST",
                        "/accounts",
                        Map.of(
                                "name",
                                name,
                                "type",
                                "CHECKING",
                                "currency",
                                currency,
                                "initialBalance",
                                balance,
                                "openingDate",
                                LocalDate.now().minusMonths(6)),
                        auth,
                        201)
                .get("id")
                .asLong();
    }

    private long category(Auth auth, String name) throws Exception {
        return json("POST", "/categories", Map.of("name", name, "type", "EXPENSE"), auth, 201)
                .get("id")
                .asLong();
    }

    private JsonNode expense(Auth auth, long account, long category, LocalDate date, int amount)
            throws Exception {
        return json(
                "POST",
                "/transactions",
                Map.of(
                        "accountId",
                        account,
                        "categoryId",
                        category,
                        "type",
                        "EXPENSE",
                        "currency",
                        "EUR",
                        "amount",
                        amount,
                        "date",
                        date,
                        "description",
                        "Private receipt"),
                auth,
                201);
    }

    private long attachment(Auth auth, long account, byte[] bytes) throws Exception {
        String result =
                mvc.perform(
                                authenticated(
                                        multipart("/api/v1/attachments")
                                                .file(
                                                        new MockMultipartFile(
                                                                "file",
                                                                "receipt.pdf",
                                                                "application/pdf",
                                                                bytes))
                                                .param("entityType", "ACCOUNT")
                                                .param("entityId", Long.toString(account)),
                                        auth))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return mapper.readTree(result).get("id").asLong();
    }

    @Autowired private org.openfinance.repository.ImportSessionRepository importSessions;
    @Autowired private org.openfinance.repository.ExchangeRateRepository exchangeRates;

    private long liability(int principal, int balance) throws Exception {
        return json(
                        "POST",
                        "/liabilities",
                        Map.of(
                                "name",
                                "Loan",
                                "type",
                                "LOAN",
                                "principal",
                                principal,
                                "currentBalance",
                                balance,
                                "currency",
                                "EUR",
                                "startDate",
                                LocalDate.now().minusMonths(6)),
                        owner,
                        201)
                .get("id")
                .asLong();
    }

    private JsonNode payment(
            long accountId, long liabilityId, int amount, String movement, int status)
            throws Exception {
        java.util.Map<String, Object> body =
                new java.util.HashMap<>(
                        Map.of(
                                "accountId",
                                accountId,
                                "liabilityId",
                                liabilityId,
                                "type",
                                "EXPENSE",
                                "amount",
                                amount,
                                "currency",
                                "EUR",
                                "date",
                                LocalDate.now(),
                                "description",
                                "Payment"));
        if (movement != null) body.put("movementType", movement);
        return json("POST", "/transactions", body, owner, status);
    }

    private void draw(long liabilityId, long accountId, int amount) throws Exception {
        json(
                "POST",
                "/liabilities/" + liabilityId + "/disburse",
                Map.of(
                        "amount",
                        amount,
                        "date",
                        LocalDate.now().minusDays(1),
                        "toAccountId",
                        accountId),
                owner,
                200);
    }

    private void assertDebt(long liabilityId, String expected) throws Exception {
        assertThat(
                        json("GET", "/liabilities/" + liabilityId, null, owner, 200)
                                .get("currentBalance")
                                .decimalValue())
                .isEqualByComparingTo(expected);
    }

    @Test
    void ordinaryPaymentSpansTranchesAndReversesExactly() throws Exception {
        long cash = account(owner, "Cash", "EUR", 1000);
        long loan = liability(1000, 0);
        draw(loan, cash, 100);
        draw(loan, cash, 100);
        JsonNode payment = payment(cash, loan, 150, null, 201);
        assertThat(payment.get("movementType").asText()).isEqualTo("REPAYMENT");
        assertThat(payment.get("principalAmount").decimalValue()).isEqualByComparingTo("150");
        assertDebt(loan, "50");
        JsonNode tranches = json("GET", "/liabilities/" + loan + "/tranches", null, owner, 200);
        assertThat(tranches.get(0).get("remaining").decimalValue()).isZero();
        assertThat(tranches.get(1).get("remaining").decimalValue()).isEqualByComparingTo("50");
        json("DELETE", "/transactions/" + payment.get("id").asLong(), null, owner, 204);
        assertDebt(loan, "200");
    }

    @Test
    void plannedTrancheDoesNotEraseOpeningDebt() throws Exception {
        long cash = account(owner, "Cash", "EUR", 1000);
        long loan = liability(200, 200);
        json(
                "POST",
                "/liabilities/" + loan + "/tranches",
                Map.of("plannedAmount", 100),
                owner,
                201);
        JsonNode payment = payment(cash, loan, 25, null, 201);
        assertDebt(loan, "175");
        json("DELETE", "/transactions/" + payment.get("id").asLong(), null, owner, 204);
        assertDebt(loan, "200");
        draw(loan, cash, 100);
        assertDebt(loan, "300");
    }

    @Test
    void overpaymentRollsBackCashDebtAndTransaction() throws Exception {
        long cash = account(owner, "Cash", "EUR", 1000);
        long loan = liability(50, 50);
        payment(cash, loan, 80, "REPAYMENT", 400);
        assertDebt(loan, "50");
        assertThat(json("GET", "/accounts/" + cash, null, owner, 200).get("balance").decimalValue())
                .isEqualByComparingTo("1000");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM transactions WHERE user_id = ?",
                                Integer.class,
                                owner.id()))
                .isZero();
    }

    @Test
    void chargesNeverReducePrincipalAndReverseWithoutCreatingDebt() throws Exception {
        long cash = account(owner, "Cash", "EUR", 1000);
        long loan = liability(100, 100);
        for (String movement : List.of("INTEREST", "INSURANCE", "FEE")) {
            JsonNode payment = payment(cash, loan, 10, movement, 201);
            assertDebt(loan, "100");
            assertThat(payment.get("principalAmount").decimalValue()).isZero();
            json("DELETE", "/transactions/" + payment.get("id").asLong(), null, owner, 204);
            assertDebt(loan, "100");
        }
    }

    @Test
    void trancheAndPrincipalLedgerAmountsAreEncryptedAtRest() throws Exception {
        long cash = account(owner, "Cash", "EUR", 1000);
        long loan = liability(1000, 0);
        json(
                "POST",
                "/liabilities/" + loan + "/tranches",
                Map.of("plannedAmount", 123, "fee", 7, "notes", "Private financing note"),
                owner,
                201);
        draw(loan, cash, 123);
        payment(cash, loan, 23, null, 201);
        Map<String, Object> row =
                jdbc.queryForMap(
                        "SELECT planned_amount, drawn_amount, fee, notes"
                                + " FROM liability_tranches WHERE liability_id = ?",
                        loan);
        for (Object value : row.values()) {
            assertThat(value.toString())
                    .doesNotContain("Private financing note")
                    .hasSizeGreaterThan(30);
        }
        assertThat(
                        jdbc.queryForObject(
                                "SELECT amount FROM liability_principal_allocations WHERE user_id = ?",
                                String.class,
                                owner.id()))
                .hasSizeGreaterThan(30);
    }

    private Map<String, Object> propertyBody(String name, int price, int value) {
        return new java.util.HashMap<>(
                Map.of(
                        "name",
                        name,
                        "propertyType",
                        "RESIDENTIAL",
                        "address",
                        "Audit address",
                        "purchasePrice",
                        price,
                        "currentValue",
                        value,
                        "currency",
                        "EUR",
                        "purchaseDate",
                        LocalDate.now().minusMonths(6)));
    }

    private JsonNode property(String name, int value) throws Exception {
        return json("POST", "/real-estate", propertyBody(name, 100, value), owner, 201);
    }

    @Test
    void undrawnCommitmentIsNotRepaidAndActualPaymentDefinesProgress() throws Exception {
        long cash = account(owner, "Cash", "EUR", 1000);
        long loan = liability(1000, 0);
        JsonNode initial = json("GET", "/liabilities/" + loan, null, owner, 200);
        assertThat(initial.get("principalPaid").decimalValue()).isZero();
        assertThat(initial.get("fundingStatus").asText()).isEqualTo("UNDRAWN");
        draw(loan, cash, 100);
        JsonNode drawn = json("GET", "/liabilities/" + loan, null, owner, 200);
        assertThat(drawn.get("principalPaid").decimalValue()).isZero();
        assertThat(drawn.get("fundedAmount").decimalValue()).isEqualByComparingTo("100");
        payment(cash, loan, 100, null, 201);
        JsonNode paid = json("GET", "/liabilities/" + loan, null, owner, 200);
        assertThat(paid.get("principalPaid").decimalValue()).isEqualByComparingTo("100");
        assertThat(paid.get("principal").decimalValue()).isEqualByComparingTo("1000");
        assertThat(paid.get("fundingStatus").asText()).isEqualTo("PAID");
    }

    @Test
    void mortgageUnlinksAndMovesFromLiabilityEditor() throws Exception {
        long loan = liability(100, 80);
        long first = property("First", 0).get("id").asLong();
        long second = property("Second", 200).get("id").asLong();
        Map<String, Object> body = propertyBody("First", 100, 0);
        body.put("mortgageId", loan);
        JsonNode linked = json("PUT", "/real-estate/" + first, body, owner, 200);
        assertThat(linked.get("equity").decimalValue()).isEqualByComparingTo("-80");
        assertThat(linked.get("equityPercentage").isNull()).isTrue();
        body.put("mortgageId", null);
        assertThat(
                        json("PUT", "/real-estate/" + first, body, owner, 200)
                                .get("mortgageId")
                                .isNull())
                .isTrue();
        Map<String, Object> loanBody =
                new java.util.HashMap<>(
                        Map.of(
                                "name",
                                "Mortgage",
                                "type",
                                "MORTGAGE",
                                "principal",
                                100,
                                "currentBalance",
                                80,
                                "currency",
                                "EUR",
                                "startDate",
                                LocalDate.now().minusMonths(6),
                                "realEstateId",
                                first));
        json("PUT", "/liabilities/" + loan, loanBody, owner, 200);
        loanBody.put("realEstateId", second);
        json("PUT", "/liabilities/" + loan, loanBody, owner, 200);
        assertThat(
                        json("GET", "/real-estate/" + first, null, owner, 200)
                                .get("mortgageId")
                                .isNull())
                .isTrue();
        assertThat(
                        json("GET", "/real-estate/" + second, null, owner, 200)
                                .get("mortgageId")
                                .asLong())
                .isEqualTo(loan);
    }

    @Test
    void propertyCurrencyUpdatesBackingAssetAndPublicAssetWritesAreRejected() throws Exception {
        JsonNode property = property("Home", 100);
        long id = property.get("id").asLong();
        long backing = property.get("assetId").asLong();
        json(
                "PUT",
                "/assets/" + backing,
                Map.of(
                        "name",
                        "Bypass",
                        "type",
                        "REAL_ESTATE",
                        "quantity",
                        1,
                        "purchasePrice",
                        100,
                        "currentPrice",
                        120,
                        "currency",
                        "EUR",
                        "purchaseDate",
                        LocalDate.now().minusMonths(6)),
                owner,
                400);
        json("DELETE", "/assets/" + backing, null, owner, 400);
        Map<String, Object> correction = propertyBody("Home", 100, 100);
        correction.put("currency", "USD");
        json("PUT", "/real-estate/" + id, correction, owner, 200);
        assertThat(json("GET", "/assets/" + backing, null, owner, 200).get("currency").asText())
                .isEqualTo("USD");
    }

    @Test
    void postedLoanCannotBeRelabeledInAnotherCurrency() throws Exception {
        long cash = account(owner, "Cash", "EUR", 1000);
        long loan = liability(1000, 0);
        draw(loan, cash, 100);
        json(
                "PUT",
                "/liabilities/" + loan,
                Map.of(
                        "name",
                        "Loan",
                        "type",
                        "LOAN",
                        "principal",
                        1000,
                        "currentBalance",
                        100,
                        "currency",
                        "USD",
                        "startDate",
                        LocalDate.now().minusMonths(6)),
                owner,
                400);
        assertThat(json("GET", "/liabilities/" + loan, null, owner, 200).get("currency").asText())
                .isEqualTo("EUR");
    }

    @Test
    void directFundingHasEquityHistoryCacheInvalidationAndDatedReversal() throws Exception {
        long loan = liability(1000, 0);
        JsonNode property = property("Home", 0);
        long propertyId = property.get("id").asLong();
        LocalDate drawDate = LocalDate.now().withDayOfMonth(1);
        // This fixture's opening valuation predates the funding. A newer appraisal would win.
        jdbc.update(
                "UPDATE real_estate_value_history SET effective_date = ? WHERE property_id = ?",
                drawDate.minusDays(1).toString(),
                propertyId);
        json("GET", "/dashboard/summary", null, owner, 200);
        json(
                "POST",
                "/liabilities/" + loan + "/disburse",
                Map.of("amount", 80, "date", drawDate, "directRealEstateId", propertyId),
                owner,
                200);
        JsonNode current = json("GET", "/real-estate/" + propertyId, null, owner, 200);
        assertThat(current.get("equity").decimalValue()).isZero();
        assertThat(current.get("allocatedDebt").decimalValue()).isEqualByComparingTo("80");
        assertThat(current.get("mortgageId").isNull()).isTrue();
        assertDebt(loan, "80");
        JsonNode summary = json("GET", "/dashboard/summary", null, owner, 200);
        assertThat(summary.get("netWorth").get("totalLiabilities").decimalValue())
                .isEqualByComparingTo("80");
        LocalDate prior = drawDate.minusDays(1);
        JsonNode history =
                json(
                        "GET",
                        "/dashboard/networth-history?startDate="
                                + prior.withDayOfMonth(1)
                                + "&endDate="
                                + prior
                                + "&recalculate=true",
                        null,
                        owner,
                        200);
        for (JsonNode point : history)
            assertThat(point.get("totalLiabilities").decimalValue()).isZero();
        long tranche =
                json("GET", "/real-estate/" + propertyId + "/drawdowns", null, owner, 200)
                        .get(0)
                        .get("id")
                        .asLong();
        json(
                "POST",
                "/tranches/" + tranche + "/reverse",
                Map.of("date", LocalDate.now()),
                owner,
                200);
        assertDebt(loan, "0");
        assertThat(
                        json("GET", "/real-estate/" + propertyId, null, owner, 200)
                                .get("currentValue")
                                .decimalValue())
                .isZero();
        JsonNode kept = json("GET", "/liabilities/" + loan + "/tranches", null, owner, 200).get(0);
        assertThat(kept.get("drawnAmount").decimalValue()).isEqualByComparingTo("80");
        assertThat(kept.get("reversedDate").asText()).isEqualTo(LocalDate.now().toString());
        json("PATCH", "/tranches/" + tranche, Map.of("status", "PLANNED"), owner, 400);
    }

    @Test
    void sameDayImprovementsHaveDeterministicHistoryAndExactNetWorth() throws Exception {
        long cash = account(owner, "Cash", "EUR", 1000);
        long propertyId = property("Home", 100).get("id").asLong();
        LocalDate date = LocalDate.now().minusMonths(1).withDayOfMonth(1);
        for (int amount : List.of(10, 20)) {
            json(
                    "POST",
                    "/transactions",
                    Map.of(
                            "accountId",
                            cash,
                            "type",
                            "EXPENSE",
                            "amount",
                            amount,
                            "currency",
                            "EUR",
                            "date",
                            date,
                            "realEstateId",
                            propertyId,
                            "movementType",
                            "CAPITAL_IMPROVEMENT"),
                    owner,
                    201);
        }
        JsonNode history =
                json(
                        "GET",
                        "/dashboard/networth-history?startDate="
                                + date
                                + "&endDate="
                                + date.withDayOfMonth(date.lengthOfMonth())
                                + "&recalculate=true",
                        null,
                        owner,
                        200);
        assertThat(history.size()).isGreaterThan(0);
        for (JsonNode point : history)
            assertThat(point.get("netWorth").decimalValue()).isEqualByComparingTo("1100");
    }

    @Test
    void sharedFinancingAllocatesDebtWithoutCashOrAssetEffects() throws Exception {
        long loan = liability(100, 100);
        JsonNode first = property("First", 200);
        JsonNode second = property("Second", 200);
        long firstAsset = first.get("assetId").asLong();
        long secondAsset = second.get("assetId").asLong();
        json(
                "PUT",
                "/liabilities/" + loan + "/asset-links",
                List.of(
                        Map.of(
                                "assetId",
                                firstAsset,
                                "relationship",
                                "FINANCING",
                                "allocationPercentage",
                                25),
                        Map.of(
                                "assetId",
                                secondAsset,
                                "relationship",
                                "FINANCING",
                                "allocationPercentage",
                                75),
                        Map.of(
                                "assetId",
                                firstAsset,
                                "relationship",
                                "COLLATERAL",
                                "allocationPercentage",
                                0)),
                owner,
                200);
        JsonNode firstAfter =
                json("GET", "/real-estate/" + first.get("id").asLong(), null, owner, 200);
        JsonNode secondAfter =
                json("GET", "/real-estate/" + second.get("id").asLong(), null, owner, 200);
        assertThat(firstAfter.get("equity").decimalValue()).isEqualByComparingTo("175");
        assertThat(secondAfter.get("equity").decimalValue()).isEqualByComparingTo("125");
        assertThat(firstAfter.get("currentValue").decimalValue()).isEqualByComparingTo("200");
        assertDebt(loan, "100");
        json(
                "PUT",
                "/liabilities/" + loan + "/asset-links",
                List.of(
                        Map.of(
                                "assetId",
                                firstAsset,
                                "relationship",
                                "FINANCING",
                                "allocationPercentage",
                                100),
                        Map.of(
                                "assetId",
                                secondAsset,
                                "relationship",
                                "FINANCING",
                                "allocationPercentage",
                                100)),
                owner,
                400);
        assertThat(json("GET", "/liabilities/" + loan + "/asset-links", null, owner, 200).size())
                .isEqualTo(3);
    }

    @Test
    void zeroValuesCreditLimitAndHistoricalDatesAreValid() throws Exception {
        json(
                "POST",
                "/assets",
                Map.of(
                        "name",
                        "Gifted car",
                        "type",
                        "VEHICLE",
                        "quantity",
                        1,
                        "purchasePrice",
                        0,
                        "currentPrice",
                        0,
                        "currency",
                        "EUR",
                        "purchaseDate",
                        LocalDate.now().minusYears(2),
                        "warrantyExpiration",
                        LocalDate.now().minusMonths(1)),
                owner,
                201);
        Map<String, Object> gifted = propertyBody("Gift", 0, 0);
        gifted.put("acquisitionType", "GIFT");
        JsonNode gift = json("POST", "/real-estate", gifted, owner, 201);
        JsonNode giftRoi =
                json("GET", "/real-estate/" + gift.get("id").asLong() + "/roi", null, owner, 200);
        assertThat(giftRoi.get("totalROI").isNull()).isTrue();
        assertThat(giftRoi.get("appreciationPercentage").isNull()).isTrue();
        Map<String, Object> card =
                new java.util.HashMap<>(
                        Map.of(
                                "name",
                                "Unused card",
                                "type",
                                "CREDIT_CARD",
                                "principal",
                                0,
                                "currentBalance",
                                0,
                                "creditLimit",
                                5000,
                                "currency",
                                "EUR",
                                "startDate",
                                LocalDate.now().minusYears(2)));
        assertThat(json("POST", "/liabilities", card, owner, 201).get("creditLimit").decimalValue())
                .isEqualByComparingTo("5000");
        card.put("type", "LOAN");
        json("POST", "/liabilities", card, owner, 400);
        card.put("principal", 1000);
        card.put("previouslyFunded", true);
        card.put("endDate", LocalDate.now().minusMonths(1));
        JsonNode paid = json("POST", "/liabilities", card, owner, 201);
        assertThat(paid.get("fundingStatus").asText()).isEqualTo("PAID");
        assertThat(paid.get("principalPaid").decimalValue()).isEqualByComparingTo("1000");
    }

    @Test
    void previewRejectsAmountsThatDoNotReconcile() throws Exception {
        long loan =
                json(
                                "POST",
                                "/liabilities",
                                Map.of(
                                        "name",
                                        "Loan",
                                        "type",
                                        "LOAN",
                                        "principal",
                                        1000,
                                        "currentBalance",
                                        1000,
                                        "currency",
                                        "EUR",
                                        "interestRate",
                                        12,
                                        "startDate",
                                        LocalDate.now().minusMonths(6)),
                                owner,
                                201)
                        .get("id")
                        .asLong();
        json(
                "GET",
                "/liabilities/" + loan + "/repayment-preview?total=5&date=" + LocalDate.now(),
                null,
                owner,
                400);
        JsonNode preview =
                json(
                        "GET",
                        "/liabilities/"
                                + loan
                                + "/repayment-preview?total=110&date="
                                + LocalDate.now(),
                        null,
                        owner,
                        200);
        assertThat(
                        preview.get("principal")
                                .decimalValue()
                                .add(preview.get("interest").decimalValue())
                                .add(preview.get("insurance").decimalValue()))
                .isEqualByComparingTo(preview.get("total").decimalValue());
    }

    @Test
    void accountBackedCreditCardDebtIsCountedOnceAndUsesAccountLedger() throws Exception {
        long card =
                json(
                                "POST",
                                "/accounts",
                                Map.of(
                                        "name",
                                        "Card",
                                        "type",
                                        "CREDIT_CARD",
                                        "currency",
                                        "EUR",
                                        "initialBalance",
                                        -100,
                                        "openingDate",
                                        LocalDate.now().minusMonths(6)),
                                owner,
                                201)
                        .get("id")
                        .asLong();
        long loan =
                json(
                                "POST",
                                "/liabilities",
                                Map.of(
                                        "name",
                                        "Card details",
                                        "type",
                                        "CREDIT_CARD",
                                        "principal",
                                        0,
                                        "currentBalance",
                                        100,
                                        "currency",
                                        "EUR",
                                        "startDate",
                                        LocalDate.now().minusMonths(6),
                                        "representedByAccountId",
                                        card),
                                owner,
                                201)
                        .get("id")
                        .asLong();
        assertThat(
                        json("GET", "/dashboard/summary", null, owner, 200)
                                .get("netWorth")
                                .get("totalLiabilities")
                                .decimalValue())
                .isEqualByComparingTo("100");
        long cash = account(owner, "Cash", "EUR", 1000);
        payment(cash, loan, 10, "REPAYMENT", 400);
        json(
                "POST",
                "/transactions",
                Map.of(
                        "accountId",
                        card,
                        "type",
                        "EXPENSE",
                        "amount",
                        20,
                        "currency",
                        "EUR",
                        "date",
                        LocalDate.now()),
                owner,
                201);
        assertDebt(loan, "120");
        Map<String, Object> financed = propertyBody("Card-financed property", 100, 100);
        financed.put("mortgageId", loan);
        JsonNode property = json("POST", "/real-estate", financed, owner, 201);
        assertThat(property.get("mortgageBalance").decimalValue()).isEqualByComparingTo("120");
        assertThat(property.get("equity").decimalValue()).isEqualByComparingTo("-20");
        assertThat(
                        json("GET", "/liabilities/" + loan, null, owner, 200)
                                .get("fundedAmount")
                                .isNull())
                .isTrue();

        Map<String, Object> sourceEdit =
                new java.util.HashMap<>(
                        Map.of(
                                "name",
                                "Card details",
                                "type",
                                "CREDIT_CARD",
                                "principal",
                                0,
                                "currentBalance",
                                120,
                                "currency",
                                "EUR",
                                "startDate",
                                LocalDate.now().minusMonths(6),
                                "representedByAccountId",
                                card));
        sourceEdit.put("currentBalance", 999);
        json("PUT", "/liabilities/" + loan, sourceEdit, owner, 409);
        sourceEdit.put("currentBalance", 120);
        sourceEdit.put("type", "LOAN");
        sourceEdit.put("principal", 120);
        json("PUT", "/liabilities/" + loan, sourceEdit, owner, 400);
        json("GET", "/liabilities/" + loan + "/breakdown", null, owner, 400);

        json("POST", "/accounts/" + card + "/close", null, owner, 400);
        json("DELETE", "/accounts/" + card, null, owner, 400);
        json(
                "PUT",
                "/accounts/" + card,
                Map.of(
                        "name",
                        "Card",
                        "type",
                        "CREDIT_CARD",
                        "currency",
                        "USD",
                        "initialBalance",
                        -120),
                owner,
                400);

        assertThat(
                        json("GET", "/dashboard/summary", null, owner, 200)
                                .get("netWorth")
                                .get("totalLiabilities")
                                .decimalValue())
                .isEqualByComparingTo("120");
    }

    @Test
    void foreignMortgageUsesConvertedPropertyDebt() throws Exception {
        jdbc.update(
                "INSERT INTO exchange_rates (base_currency, target_currency, rate, rate_date, source) VALUES (?, ?, ?, ?, ?)",
                "DKK",
                "EUR",
                new java.math.BigDecimal("0.5"),
                LocalDate.now().toString(),
                "synthetic-test");
        long loan =
                json(
                                "POST",
                                "/liabilities",
                                Map.of(
                                        "name",
                                        "Foreign loan",
                                        "type",
                                        "MORTGAGE",
                                        "principal",
                                        80,
                                        "currentBalance",
                                        80,
                                        "currency",
                                        "DKK",
                                        "startDate",
                                        LocalDate.now().minusMonths(6)),
                                owner,
                                201)
                        .get("id")
                        .asLong();
        Map<String, Object> body = propertyBody("Home", 100, 100);
        body.put("mortgageId", loan);
        JsonNode home = json("POST", "/real-estate", body, owner, 201);
        assertThat(home.get("equity").decimalValue()).isEqualByComparingTo("60");
        assertThat(home.get("mortgageBalance").decimalValue()).isEqualByComparingTo("40");
        assertThat(home.get("mortgageCurrency").asText()).isEqualTo("DKK");
        assertThat(
                        json(
                                        "GET",
                                        "/real-estate/" + home.get("id").asLong() + "/equity",
                                        null,
                                        owner,
                                        200)
                                .get("equity")
                                .decimalValue())
                .isEqualByComparingTo("60");
    }

    @Test
    void sharedPrimaryMortgageRequiresExplicitAllocation() throws Exception {
        long loan = liability(100, 100);
        Map<String, Object> body = propertyBody("First", 200, 200);
        body.put("mortgageId", loan);
        json("POST", "/real-estate", body, owner, 201);
        body.put("name", "Second");
        json("POST", "/real-estate", body, owner, 400);
    }

    @Test
    void changingRepaymentReallocatesAndDeletingItRestoresDrawnPrincipal() throws Exception {
        long cash = account(owner, "Cash", "EUR", 1000);
        long loan = liability(1000, 0);
        draw(loan, cash, 100);
        draw(loan, cash, 100);
        JsonNode payment = payment(cash, loan, 150, null, 201);
        long paymentId = payment.get("id").asLong();
        json(
                "PUT",
                "/transactions/" + paymentId,
                Map.of(
                        "accountId",
                        cash,
                        "liabilityId",
                        loan,
                        "type",
                        "EXPENSE",
                        "amount",
                        40,
                        "currency",
                        "EUR",
                        "date",
                        LocalDate.now()),
                owner,
                200);
        assertDebt(loan, "160");
        JsonNode tranches = json("GET", "/liabilities/" + loan + "/tranches", null, owner, 200);
        assertThat(tranches.get(0).get("remaining").decimalValue()).isEqualByComparingTo("60");
        assertThat(tranches.get(1).get("remaining").decimalValue()).isEqualByComparingTo("100");
        json("DELETE", "/transactions/" + paymentId, null, owner, 204);
        assertDebt(loan, "200");
    }

    @Test
    void physicalFinancingIsOwnedAndAssetDeletionDoesNotExtinguishDebt() throws Exception {
        long loan = liability(100, 100);
        Map<String, Object> body =
                new java.util.HashMap<>(
                        Map.of(
                                "name",
                                "Car",
                                "type",
                                "VEHICLE",
                                "quantity",
                                1,
                                "purchasePrice",
                                0,
                                "currentPrice",
                                0,
                                "currency",
                                "EUR",
                                "purchaseDate",
                                LocalDate.now()));
        long asset = json("POST", "/assets", body, owner, 201).get("id").asLong();
        Object links =
                List.of(
                        Map.of(
                                "assetId",
                                asset,
                                "relationship",
                                "FINANCING",
                                "allocationPercentage",
                                100));
        json("PUT", "/liabilities/" + loan + "/asset-links", links, owner, 200);
        other = register("other", "OtherMaster123!");
        json("GET", "/assets/" + asset + "/liabilities", null, other, 404);
        json("PUT", "/liabilities/" + loan + "/asset-links", links, other, 404);
        json(
                "PUT",
                "/liabilities/" + loan + "/asset-links",
                List.of(
                        Map.of(
                                "assetId",
                                asset,
                                "relationship",
                                "FINANCING",
                                "allocationPercentage",
                                0)),
                owner,
                400);
        assertThat(json("GET", "/assets/" + asset + "/liabilities", null, owner, 200).size())
                .isEqualTo(1);
        json("DELETE", "/assets/" + asset, null, owner, 204);
        assertDebt(loan, "100");
        body.put("acquisitionType", "PLANNED");
        body.put("purchaseDate", LocalDate.now().plusDays(3));
        body.put("currentPrice", 1);
        json("POST", "/assets", body, owner, 400);
        body.put("currentPrice", 0);
        json("POST", "/assets", body, owner, 201);
    }

    @Test
    void sameDateImprovementEditsAndReversalsKeepHistoryAndCashInAgreement() throws Exception {
        long cash = account(owner, "Cash", "EUR", 1000);
        long home = property("Home", 100).get("id").asLong();
        LocalDate date = LocalDate.now().minusMonths(1).withDayOfMonth(1);
        Map<String, Object> body =
                new java.util.HashMap<>(
                        Map.of(
                                "accountId",
                                cash,
                                "type",
                                "EXPENSE",
                                "amount",
                                10,
                                "currency",
                                "EUR",
                                "date",
                                date,
                                "realEstateId",
                                home,
                                "movementType",
                                "CAPITAL_IMPROVEMENT"));
        long first = json("POST", "/transactions", body, owner, 201).get("id").asLong();
        body.put("amount", 20);
        long second = json("POST", "/transactions", body, owner, 201).get("id").asLong();
        body.put("amount", 15);
        json("PUT", "/transactions/" + first, body, owner, 200);
        json("DELETE", "/transactions/" + second, null, owner, 204);
        assertThat(
                        json("GET", "/real-estate/" + home, null, owner, 200)
                                .get("currentValue")
                                .decimalValue())
                .isEqualByComparingTo("115");
        JsonNode history =
                json(
                        "GET",
                        "/dashboard/networth-history?startDate="
                                + date
                                + "&endDate="
                                + date.withDayOfMonth(date.lengthOfMonth())
                                + "&recalculate=true",
                        null,
                        owner,
                        200);
        assertThat(history.size()).isPositive();
        for (JsonNode point : history)
            assertThat(point.get("netWorth").decimalValue()).isEqualByComparingTo("1100");
    }

    @Test
    void encryptedDrawAllocationsAndFinancingSurviveRotationAndPortableRestore() throws Exception {
        long cash = account(owner, "Cash", "EUR", 1000);
        long home = property("Home", 100).get("id").asLong();
        long loan = liability(1000, 0);
        json(
                "POST",
                "/liabilities/" + loan + "/disburse",
                Map.of("amount", 80, "date", LocalDate.now(), "directRealEstateId", home),
                owner,
                200);
        payment(cash, loan, 30, null, 201);
        long planned =
                json(
                                "POST",
                                "/liabilities/" + loan + "/tranches",
                                Map.of(
                                        "plannedAmount",
                                        25,
                                        "fee",
                                        2,
                                        "notes",
                                        "Private funding note"),
                                owner,
                                201)
                        .get("id")
                        .asLong();
        for (String query :
                List.of(
                        "SELECT planned_amount FROM liability_tranches WHERE id = " + planned,
                        "SELECT notes FROM liability_tranches WHERE id = " + planned,
                        "SELECT fee FROM liability_tranches WHERE id = " + planned,
                        "SELECT amount FROM liability_principal_allocations WHERE user_id = "
                                + owner.id(),
                        "SELECT recorded_value FROM real_estate_value_history WHERE user_id = "
                                + owner.id()
                                + " LIMIT 1",
                        "SELECT allocation_percentage FROM liability_asset_links WHERE user_id = "
                                + owner.id())) {
            assertThat(
                            EncryptedUserDataService.looksEncrypted(
                                    jdbc.queryForObject(query, String.class)))
                    .isTrue();
        }
        JsonNode rotated =
                json(
                        "PUT",
                        "/users/me/master-password",
                        Map.of(
                                "currentMasterPassword",
                                MASTER,
                                "newMasterPassword",
                                "ReplacementMaster123!"),
                        owner,
                        200);
        owner =
                new Auth(
                        owner.id(),
                        owner.username(),
                        owner.token(),
                        rotated.get("encryptionKey").asText());
        assertDebt(loan, "50");
        assertThat(
                        json("GET", "/liabilities/" + loan + "/tranches", null, owner, 200)
                                .get(1)
                                .get("notes")
                                .asText())
                .isEqualTo("Private funding note");
        JsonNode linked = json("GET", "/real-estate/" + home + "/loan-movements", null, owner, 200);
        assertThat(linked.size()).isEqualTo(1);
        assertThat(linked.get(0).get("principalAllocations").get(0).get("amount").decimalValue())
                .isEqualByComparingTo("30");
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.getMetaData().getURL().startsWith("jdbc:sqlite:")) {
                // Portable archives are an existing SQLite-only capability.
                assertThat(
                                json("POST", "/backup/create", Map.of(), owner, 400)
                                        .get("message")
                                        .asText())
                        .contains("SQLite");
                return;
            }
        }
        JsonNode backup = json("POST", "/backup/create", Map.of(), owner, 201);
        byte[] bytes =
                mvc.perform(
                                authenticated(
                                        get(
                                                "/api/v1/backup/"
                                                        + backup.get("id").asLong()
                                                        + "/download"),
                                        owner))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray();
        other = register("restored", "DestinationMaster123!");
        mvc.perform(
                        authenticated(
                                multipart("/api/v1/backup/restore/upload")
                                        .file(
                                                new MockMultipartFile(
                                                        "file",
                                                        "portable.ofbak",
                                                        "application/gzip",
                                                        bytes))
                                        .param("masterPassword", "ReplacementMaster123!"),
                                other))
                .andExpect(status().isOk());
        owner = other;
        JsonNode restoredLoan = json("GET", "/liabilities", null, owner, 200).get(0);
        long restoredId = restoredLoan.get("id").asLong();
        assertDebt(restoredId, "50");
        JsonNode restoredLinks =
                json("GET", "/liabilities/" + restoredId + "/asset-links", null, owner, 200);
        assertThat(restoredLinks.size()).isEqualTo(1);
        JsonNode payments =
                json("GET", "/liabilities/" + restoredId + "/transactions", null, owner, 200);
        json("DELETE", "/transactions/" + payments.get(0).get("id").asLong(), null, owner, 204);
        assertDebt(restoredId, "80");
        JsonNode tranches =
                json("GET", "/liabilities/" + restoredId + "/tranches", null, owner, 200);
        json(
                "POST",
                "/tranches/" + tranches.get(0).get("id").asLong() + "/reverse",
                Map.of("date", LocalDate.now()),
                owner,
                200);
        assertDebt(restoredId, "0");
    }

    @Test
    void plannedPropertyAcquisitionSynchronizesBackingAssetAndValuation() throws Exception {
        Map<String, Object> body = propertyBody("Planned home", 0, 0);
        body.put("acquisitionType", "PLANNED");
        body.put("purchaseDate", LocalDate.now().plusYears(1));
        JsonNode property = json("POST", "/real-estate", body, owner, 201);
        long id = property.get("id").asLong();
        long assetId = property.get("assetId").asLong();
        assertThat(
                        json("GET", "/assets/" + assetId, null, owner, 200)
                                .get("acquisitionType")
                                .asText())
                .isEqualTo("PLANNED");
        long cash = account(owner, "Cash", "EUR", 1000);
        Map<String, Object> improvement =
                Map.of(
                        "accountId",
                        cash,
                        "realEstateId",
                        id,
                        "type",
                        "EXPENSE",
                        "amount",
                        10,
                        "currency",
                        "EUR",
                        "date",
                        LocalDate.now(),
                        "movementType",
                        "CAPITAL_IMPROVEMENT");
        json("POST", "/transactions", improvement, owner, 400);
        body.put("acquisitionType", "PURCHASE");
        body.put("purchaseDate", LocalDate.now());
        body.put("purchasePrice", 100);
        body.put("currentValue", 100);
        json("PUT", "/real-estate/" + id, body, owner, 200);
        JsonNode backing = json("GET", "/assets/" + assetId, null, owner, 200);
        assertThat(backing.get("acquisitionType").asText()).isEqualTo("PURCHASE");
        assertThat(backing.get("purchasePrice").decimalValue()).isEqualByComparingTo("100");
        assertThat(backing.get("currentPrice").decimalValue()).isEqualByComparingTo("100");
        long tx = json("POST", "/transactions", improvement, owner, 201).get("id").asLong();
        json("DELETE", "/transactions/" + tx, null, owner, 204);
        assertThat(
                        json("GET", "/real-estate/" + id, null, owner, 200)
                                .get("currentValue")
                                .decimalValue())
                .isEqualByComparingTo("100");
    }
}
