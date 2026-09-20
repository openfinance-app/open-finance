package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
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
class AuditFollowupIntegrationTest {
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
                    Files.createTempFile(Path.of("target", "test-dbs"), "followup-wal-", ".db");
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
        other = register("destination", "DestinationMaster123!");
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

    @Test
    void rejectsForeignPrivateCatalogReadsAndLinks() throws Exception {
        long institution =
                json(
                                "POST",
                                "/institutions",
                                Map.of("name", "Private institution", "country", "FR"),
                                owner,
                                201)
                        .get("id")
                        .asLong();
        json("GET", "/institutions/" + institution, null, other, 404);
        json(
                "POST",
                "/accounts",
                Map.of(
                        "name",
                        "Foreign link",
                        "type",
                        "CHECKING",
                        "currency",
                        "EUR",
                        "initialBalance",
                        0,
                        "institutionId",
                        institution),
                other,
                404);
        json(
                "POST",
                "/liabilities",
                Map.of(
                        "name",
                        "Foreign loan",
                        "type",
                        "PERSONAL_LOAN",
                        "principal",
                        100,
                        "currentBalance",
                        100,
                        "interestRate",
                        0,
                        "currency",
                        "EUR",
                        "startDate",
                        LocalDate.now(),
                        "institutionId",
                        institution),
                other,
                404);
        long category = category(owner, "Private category");
        long payee =
                json(
                                "POST",
                                "/payees",
                                Map.of("name", "Private payee", "categoryId", category),
                                owner,
                                201)
                        .get("id")
                        .asLong();
        json("GET", "/payees/" + payee, null, other, 404);
        json(
                "POST",
                "/payees",
                Map.of("name", "Foreign category", "categoryId", category),
                other,
                404);
    }

    @Test
    void encryptsImportedPayloadsAndSavedSimulations() throws Exception {
        String payload = "{\"privateMarker\":\"owner financial details\"}";
        org.openfinance.security.EncryptionContext.setKey(keys.getKey(owner.id()).orElseThrow());
        long sessionId;
        try {
            sessionId =
                    importSessions
                            .saveAndFlush(
                                    org.openfinance.entity.ImportSession.builder()
                                            .userId(owner.id())
                                            .uploadId(UUID.randomUUID().toString())
                                            .fileName("private.qif")
                                            .fileFormat("QIF")
                                            .metadata(payload)
                                            .build())
                            .getId();
        } finally {
            org.openfinance.security.EncryptionContext.clear();
        }
        String stored =
                jdbc.queryForObject(
                        "SELECT metadata FROM import_sessions WHERE id = ?",
                        String.class,
                        sessionId);
        assertThat(stored).doesNotContain("privateMarker").doesNotContain("financial details");
        long simulation =
                json(
                                "POST",
                                "/real-estate-simulations",
                                Map.of(
                                        "name",
                                        "Private simulation",
                                        "simulationType",
                                        "buy_rent",
                                        "data",
                                        payload),
                                owner,
                                201)
                        .get("id")
                        .asLong();
        String simulationData =
                jdbc.queryForObject(
                        "SELECT data FROM real_estate_simulations WHERE id = ?",
                        String.class,
                        simulation);
        assertThat(simulationData).doesNotContain("privateMarker");
    }

    private void auditExchangeRate() {
        org.openfinance.entity.ExchangeRate rate =
                exchangeRates
                        .findByBaseCurrencyAndTargetCurrencyAndRateDate(
                                "EUR", "USD", LocalDate.now())
                        .orElseGet(
                                () ->
                                        org.openfinance.entity.ExchangeRate.builder()
                                                .baseCurrency("EUR")
                                                .targetCurrency("USD")
                                                .rateDate(LocalDate.now())
                                                .source("audit")
                                                .build());
        rate.setRate(new java.math.BigDecimal("2"));
        exchangeRates.saveAndFlush(rate);
    }

    @Test
    void renamingPreservesHistoryAndCurrencyChangesPreserveOriginalTransactions() throws Exception {
        long account = account(owner, "Currency account", "EUR", 1000);
        long category = category(owner, "Expense");
        long tx =
                expense(owner, account, category, LocalDate.now().minusDays(1), 100)
                        .get("id")
                        .asLong();
        json(
                "PUT",
                "/accounts/" + account,
                Map.of(
                        "name",
                        "Renamed",
                        "type",
                        "CHECKING",
                        "currency",
                        "EUR",
                        "initialBalance",
                        900),
                owner,
                200);
        JsonNode history =
                json(
                        "GET",
                        "/accounts/" + account + "/balance-history?period=ALL",
                        null,
                        owner,
                        200);
        assertThat(history.get(history.size() - 1).get("balance").decimalValue())
                .isEqualByComparingTo("900");
        auditExchangeRate();
        JsonNode converted =
                json(
                        "PUT",
                        "/accounts/" + account,
                        Map.of(
                                "name",
                                "USD account",
                                "type",
                                "CHECKING",
                                "currency",
                                "USD",
                                "initialBalance",
                                900,
                                "balanceCurrency",
                                "EUR"),
                        owner,
                        200);
        assertThat(converted.get("balance").decimalValue()).isEqualByComparingTo("1800");
        JsonNode original = json("GET", "/transactions/" + tx, null, owner, 200);
        assertThat(original.get("currency").asText()).isEqualTo("EUR");
        assertThat(original.get("amount").decimalValue()).isEqualByComparingTo("100");
        assertThat(original.get("accountAmount").decimalValue()).isEqualByComparingTo("200");
        json("DELETE", "/transactions/" + tx, null, owner, 204);
        assertThat(
                        json("GET", "/accounts/" + account, null, owner, 200)
                                .get("balance")
                                .decimalValue())
                .isEqualByComparingTo("2000");
    }

    @Test
    void concurrentExpensesAllContributeToTheBalance() throws Exception {
        long account = account(owner, "Concurrent account", "EUR", 1000);
        long category = category(owner, "Concurrent expenses");
        json("GET", "/accounts/" + account, null, owner, 200);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(12)) {
            var start = new java.util.concurrent.CountDownLatch(1);
            var futures = new java.util.ArrayList<java.util.concurrent.Future<JsonNode>>();
            for (int i = 0; i < 12; i++)
                futures.add(
                        executor.submit(
                                () -> {
                                    start.await();
                                    return expense(owner, account, category, LocalDate.now(), 10);
                                }));
            start.countDown();
            for (var future : futures) future.get(45, java.util.concurrent.TimeUnit.SECONDS);
        }
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM transactions WHERE account_id = ?",
                                Integer.class,
                                account))
                .isEqualTo(12);
        assertThat(
                        json("GET", "/accounts/" + account, null, owner, 200)
                                .get("balance")
                                .decimalValue())
                .isEqualByComparingTo("880");
    }

    @Test
    void manualRecurringProcessingCatchesUpOnlyTheOwner() throws Exception {
        long account = account(owner, "My recurring account", "EUR", 1000);
        long otherAccount = account(other, "Other recurring account", "EUR", 1000);
        for (Auth user : List.of(owner, other)) {
            json(
                    "POST",
                    "/recurring-transactions",
                    Map.of(
                            "accountId",
                            user == owner ? account : otherAccount,
                            "description",
                            "Daily payment",
                            "type",
                            "EXPENSE",
                            "amount",
                            25,
                            "currency",
                            "EUR",
                            "frequency",
                            "DAILY",
                            "startDate",
                            LocalDate.now().minusDays(3),
                            "nextOccurrence",
                            LocalDate.now().minusDays(3)),
                    user,
                    201);
        }
        JsonNode result = json("POST", "/recurring-transactions/process", null, owner, 200);
        assertThat(result.get("processedCount").asInt()).isEqualTo(4);
        assertThat(
                        json("GET", "/accounts/" + account, null, owner, 200)
                                .get("balance")
                                .decimalValue())
                .isEqualByComparingTo("900");
        assertThat(
                        json("GET", "/accounts/" + otherAccount, null, other, 200)
                                .get("balance")
                                .decimalValue())
                .isEqualByComparingTo("1000");
        assertThat(
                        json("POST", "/recurring-transactions/process", null, owner, 200)
                                .get("processedCount")
                                .asInt())
                .isZero();
    }

    @Test
    void supportsDatedBudgetPeriodsAndCarriesUnusedFunds() throws Exception {
        long account = account(owner, "Budget account", "EUR", 1000);
        long category = category(owner, "Rollover");
        LocalDate current = LocalDate.now().withDayOfMonth(1);
        expense(owner, account, category, current.minusDays(10), 40);
        Map<String, Object> previous =
                Map.of(
                        "categoryId",
                        category,
                        "amount",
                        100,
                        "currency",
                        "EUR",
                        "period",
                        "MONTHLY",
                        "rollover",
                        true,
                        "startDate",
                        current.minusMonths(1),
                        "endDate",
                        current.minusDays(1));
        json("POST", "/budgets", previous, owner, 201);
        Map<String, Object> next =
                Map.of(
                        "categoryId",
                        category,
                        "amount",
                        100,
                        "currency",
                        "EUR",
                        "period",
                        "MONTHLY",
                        "rollover",
                        true,
                        "startDate",
                        current,
                        "endDate",
                        current.plusMonths(1).minusDays(1));
        long id = json("POST", "/budgets", next, owner, 201).get("id").asLong();
        assertThat(
                        json("GET", "/budgets/" + id + "/progress", null, owner, 200)
                                .get("budgeted")
                                .decimalValue())
                .isEqualByComparingTo("160");
        json("POST", "/budgets", next, owner, 400);
    }

    @Test
    void rejectsDescendantCategoryMovesAndHonorsExportSelection() throws Exception {
        long parent = category(owner, "Root");
        long child =
                json(
                                "POST",
                                "/categories",
                                Map.of("name", "Child", "type", "EXPENSE", "parentId", parent),
                                owner,
                                201)
                        .get("id")
                        .asLong();
        json(
                "PUT",
                "/categories/" + parent,
                Map.of("name", "Root", "type", "EXPENSE", "parentId", child),
                owner,
                400);
        long account = account(owner, "Export account", "EUR", 1000);
        expense(owner, account, parent, LocalDate.now().minusDays(10), 10);
        long deleted = expense(owner, account, parent, LocalDate.now(), 20).get("id").asLong();
        json("DELETE", "/transactions/" + deleted, null, owner, 204);
        JsonNode exported =
                json(
                                "POST",
                                "/data/export",
                                Map.of(
                                        "format",
                                        "JSON",
                                        "includeTransactions",
                                        true,
                                        "includeDeleted",
                                        true,
                                        "startDate",
                                        LocalDate.now()),
                                owner,
                                200)
                        .get("transactions");
        assertThat(exported.size()).isEqualTo(1);
        assertThat(exported.get(0).get("id").asLong()).isEqualTo(deleted);
    }

    private JsonNode parsedImport(String filename, String content, Long accountId)
            throws Exception {
        String uploaded =
                mvc.perform(
                                authenticated(
                                        multipart("/api/v1/import/upload")
                                                .file(
                                                        new MockMultipartFile(
                                                                "file",
                                                                filename,
                                                                "application/octet-stream",
                                                                content.getBytes(
                                                                        StandardCharsets.UTF_8))),
                                        owner))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("uploadId", mapper.readTree(uploaded).get("uploadId").asText());
        body.put("originalFileName", filename);
        body.put("accountId", accountId);
        JsonNode session = json("POST", "/import/process", body, owner, 200);
        assertThat(session.get("status").asText()).isIn("PARSED", "REVIEWING");
        return session;
    }

    private JsonNode completedImport(long id) throws Exception {
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(20).toNanos();
        JsonNode session;
        do {
            session = json("GET", "/import/sessions/" + id, null, owner, 200);
            if (List.of("COMPLETED", "FAILED").contains(session.get("status").asText())) break;
            Thread.sleep(25);
        } while (System.nanoTime() < deadline);
        assertThat(session.get("status").asText()).as(session.toString()).isEqualTo("COMPLETED");
        return session;
    }

    @Test
    void acceptsOnlyOneConcurrentImportConfirmation() throws Exception {
        long account = account(owner, "Imported account", "EUR", 1000);
        long id =
                parsedImport(
                                "once.qif",
                                "!Type:Bank\nD09/08/2026\nT-10.00\nMFirst\n^\nD09/08/2026\nT-20.00\nMSecond\n^\n",
                                account)
                        .get("id")
                        .asLong();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT metadata FROM import_sessions WHERE id = ?",
                                String.class,
                                id))
                .doesNotContain("First", "Second");
        try (java.util.concurrent.ExecutorService executor =
                java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
            java.util.List<java.util.concurrent.Future<Integer>> futures =
                    new java.util.ArrayList<>();
            for (int i = 0; i < 2; i++)
                futures.add(
                        executor.submit(
                                () -> {
                                    start.await();
                                    return mvc.perform(
                                                    authenticated(
                                                            post("/api/v1/import/sessions/"
                                                                            + id
                                                                            + "/confirm")
                                                                    .contentType(
                                                                            MediaType
                                                                                    .APPLICATION_JSON)
                                                                    .content(
                                                                            "{\"accountId\":"
                                                                                    + account
                                                                                    + ",\"skipDuplicates\":false}"),
                                                            owner))
                                            .andReturn()
                                            .getResponse()
                                            .getStatus();
                                }));
            start.countDown();
            assertThat(List.of(futures.get(0).get(), futures.get(1).get()))
                    .containsExactlyInAnyOrder(202, 400);
        }
        completedImport(id);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM transactions WHERE account_id = ?",
                                Integer.class,
                                account))
                .isEqualTo(2);
        assertThat(
                        json("GET", "/accounts/" + account, null, owner, 200)
                                .get("balance")
                                .decimalValue())
                .isEqualByComparingTo("970");
    }

    @Test
    void keepsAnExplicitZeroStatementBalance() throws Exception {
        String ofx =
                Files.readString(Path.of("src/test/resources/samples/standard_sgml.ofx"))
                        .replaceAll("(?s)<BALAMT>[^<\\r\\n]+", "<BALAMT>0.00");
        long id = parsedImport("zero.ofx", ofx, null).get("id").asLong();
        json(
                "POST",
                "/import/sessions/" + id + "/confirm",
                Map.of("skipDuplicates", true),
                owner,
                202);
        completedImport(id);
        JsonNode accounts = json("GET", "/accounts", null, owner, 200);
        assertThat(accounts.size()).isEqualTo(1);
        assertThat(accounts.get(0).get("balance").decimalValue()).isEqualByComparingTo("0");
    }

    @Test
    void postsOverdueOccurrencesThroughTheEndDate() throws Exception {
        long account = account(owner, "Expired recurring account", "EUR", 1000);
        LocalDate first = LocalDate.now().minusDays(5);
        json(
                "POST",
                "/recurring-transactions",
                Map.of(
                        "accountId",
                        account,
                        "description",
                        "Expired bill",
                        "type",
                        "EXPENSE",
                        "amount",
                        25,
                        "currency",
                        "EUR",
                        "frequency",
                        "DAILY",
                        "startDate",
                        first,
                        "nextOccurrence",
                        first,
                        "endDate",
                        LocalDate.now().plusDays(1)),
                owner,
                201);
        jdbc.update(
                "UPDATE recurring_transactions SET end_date = ? WHERE user_id = ?",
                first.plusDays(2).toString(),
                owner.id());
        assertThat(
                        json("POST", "/recurring-transactions/process", null, owner, 200)
                                .get("processedCount")
                                .asInt())
                .isEqualTo(3);
        assertThat(
                        json("GET", "/accounts/" + account, null, owner, 200)
                                .get("balance")
                                .decimalValue())
                .isEqualByComparingTo("925");
        assertThat(
                        json("POST", "/recurring-transactions/process", null, owner, 200)
                                .get("processedCount")
                                .asInt())
                .isZero();
    }

    @Test
    void synchronizesPropertyImprovementsAndTheirReversalWithTheBackingAsset() throws Exception {
        long account = account(owner, "Property funding", "EUR", 1000);
        JsonNode property =
                json(
                        "POST",
                        "/real-estate",
                        Map.of(
                                "name",
                                "House",
                                "address",
                                "Test street",
                                "propertyType",
                                "RESIDENTIAL",
                                "purchasePrice",
                                200,
                                "currentValue",
                                250,
                                "purchaseDate",
                                LocalDate.now().minusMonths(1),
                                "currency",
                                "EUR"),
                        owner,
                        201);
        long propertyId = property.get("id").asLong();
        long assetId =
                jdbc.queryForObject(
                        "SELECT asset_id FROM real_estate_properties WHERE id = ?",
                        Long.class,
                        propertyId);
        long tx =
                json(
                                "POST",
                                "/transactions",
                                Map.of(
                                        "accountId",
                                        account,
                                        "type",
                                        "EXPENSE",
                                        "amount",
                                        30,
                                        "currency",
                                        "EUR",
                                        "date",
                                        LocalDate.now(),
                                        "description",
                                        "Renovation",
                                        "movementType",
                                        "CAPITAL_IMPROVEMENT",
                                        "realEstateId",
                                        propertyId),
                                owner,
                                201)
                        .get("id")
                        .asLong();
        assertThat(
                        json("GET", "/real-estate/" + propertyId, null, owner, 200)
                                .get("currentValue")
                                .decimalValue())
                .isEqualByComparingTo("280");
        assertThat(
                        json("GET", "/assets/" + assetId, null, owner, 200)
                                .get("currentPrice")
                                .decimalValue())
                .isEqualByComparingTo("280");
        json("DELETE", "/transactions/" + tx, null, owner, 204);
        assertThat(
                        json("GET", "/assets/" + assetId, null, owner, 200)
                                .get("currentPrice")
                                .decimalValue())
                .isEqualByComparingTo("250");
    }

    @Test
    void restoresLegacyOrphanReceiptBytesWithoutAttachingThemToAnotherUsersEntity()
            throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assumeTrue(connection.getMetaData().getURL().startsWith("jdbc:sqlite:"));
        }
        long account = account(owner, "Source receipts", "EUR", 1000);
        expense(
                owner,
                account,
                category(owner, "Portable currency"),
                LocalDate.now().minusDays(1),
                100);
        auditExchangeRate();
        json(
                "PUT",
                "/accounts/" + account,
                Map.of(
                        "name",
                        "Portable USD account",
                        "type",
                        "CHECKING",
                        "currency",
                        "USD",
                        "initialBalance",
                        900,
                        "balanceCurrency",
                        "EUR"),
                owner,
                200);
        byte[] receipt = "%PDF-1.4\nlegacy orphan receipt\n%%EOF".getBytes(StandardCharsets.UTF_8);
        long attachment = attachment(owner, account, receipt);
        jdbc.update(
                "UPDATE attachments SET entity_type = 'ASSET', entity_id = 987654321 WHERE id = ?",
                attachment);
        long backup = json("POST", "/backup/create", Map.of(), owner, 201).get("id").asLong();
        byte[] bytes =
                mvc.perform(authenticated(get("/api/v1/backup/" + backup + "/download"), owner))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray();
        mvc.perform(
                        authenticated(
                                multipart("/api/v1/backup/restore/upload")
                                        .file(
                                                new MockMultipartFile(
                                                        "file",
                                                        "portable.ofbak",
                                                        "application/gzip",
                                                        bytes))
                                        .param("masterPassword", MASTER),
                                other))
                .andExpect(status().isOk());
        long restored =
                jdbc.queryForObject(
                        "SELECT id FROM attachments WHERE user_id = ?", Long.class, other.id());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT entity_id FROM attachments WHERE id = ?",
                                Long.class,
                                restored))
                .isNegative();
        byte[] downloaded =
                mvc.perform(
                                authenticated(
                                        get("/api/v1/attachments/" + restored + "/download"),
                                        other))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray();
        assertThat(downloaded).isEqualTo(receipt);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM account_currency_changes WHERE user_id = ?",
                                Integer.class,
                                other.id()))
                .isEqualTo(1);
        long restoredAccount = json("GET", "/accounts", null, other, 200).get(0).get("id").asLong();
        JsonNode transaction =
                json("GET", "/transactions?accountId=" + restoredAccount, null, other, 200).get(0);
        assertThat(transaction.get("currency").asText()).isEqualTo("EUR");
        assertThat(transaction.get("accountAmount").decimalValue()).isEqualByComparingTo("200");
        json("DELETE", "/transactions/" + transaction.get("id").asLong(), null, other, 204);
        assertThat(
                        json("GET", "/accounts/" + restoredAccount, null, other, 200)
                                .get("balance")
                                .decimalValue())
                .isEqualByComparingTo("2000");
    }

    @Test
    void keepsPurchasePriceFixedAndRespectsTheLatestEffectiveValuation() throws Exception {
        long property =
                json(
                                "POST",
                                "/real-estate",
                                Map.of(
                                        "name",
                                        "Partly funded house",
                                        "address",
                                        "Test street",
                                        "propertyType",
                                        "RESIDENTIAL",
                                        "purchasePrice",
                                        200000,
                                        "currentValue",
                                        0,
                                        "purchaseDate",
                                        LocalDate.now().minusMonths(2),
                                        "currency",
                                        "EUR"),
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
                                        "Mortgage",
                                        "type",
                                        "MORTGAGE",
                                        "principal",
                                        200000,
                                        "currentBalance",
                                        0,
                                        "interestRate",
                                        0,
                                        "currency",
                                        "EUR",
                                        "startDate",
                                        LocalDate.now().minusMonths(2)),
                                owner,
                                201)
                        .get("id")
                        .asLong();
        long asset =
                jdbc.queryForObject(
                        "SELECT asset_id FROM real_estate_properties WHERE id = ?",
                        Long.class,
                        property);
        // The property's initial appraisal is dated today, so a backdated draw does not
        // overwrite it. A later entry effective today does update it; debt still accumulates.
        for (int amount : List.of(20000, 40000)) {
            json(
                    "POST",
                    "/liabilities/" + loan + "/disburse",
                    Map.of(
                            "amount",
                            amount,
                            "directRealEstateId",
                            property,
                            "date",
                            amount == 20000 ? LocalDate.now().minusMonths(2) : LocalDate.now()),
                    owner,
                    200);
            JsonNode updated = json("GET", "/real-estate/" + property, null, owner, 200);
            assertThat(updated.get("purchasePrice").decimalValue()).isEqualByComparingTo("200000");
            assertThat(updated.get("currentValue").decimalValue())
                    .isEqualByComparingTo(amount == 20000 ? "0" : "40000");
            assertThat(
                            json("GET", "/assets/" + asset, null, owner, 200)
                                    .get("currentPrice")
                                    .decimalValue())
                    .isEqualByComparingTo(amount == 20000 ? "0" : "40000");
        }
        assertThat(
                        json("GET", "/liabilities/" + loan, null, owner, 200)
                                .get("currentBalance")
                                .decimalValue())
                .isEqualByComparingTo("60000");
    }

    @Test
    void currencyChangePreservesTransferReversalsAndBackdatedEdits() throws Exception {
        long source = account(owner, "Transfer source", "EUR", 1000);
        long target = account(owner, "Transfer target", "EUR", 500);
        long category = category(owner, "Original currency expenses");
        JsonNode transfer =
                json(
                        "POST",
                        "/transactions/transfer",
                        Map.of(
                                "accountId",
                                source,
                                "toAccountId",
                                target,
                                "amount",
                                100,
                                "currency",
                                "EUR",
                                "type",
                                "TRANSFER",
                                "date",
                                LocalDate.now().minusDays(1)),
                        owner,
                        201);
        auditExchangeRate();
        json(
                "PUT",
                "/accounts/" + source,
                Map.of(
                        "name",
                        "USD source",
                        "type",
                        "CHECKING",
                        "currency",
                        "USD",
                        "initialBalance",
                        900,
                        "balanceCurrency",
                        "EUR"),
                owner,
                200);
        long expense =
                expense(owner, source, category, LocalDate.now().minusDays(2), 50)
                        .get("id")
                        .asLong();
        assertThat(
                        json("GET", "/accounts/" + source, null, owner, 200)
                                .get("balance")
                                .decimalValue())
                .isEqualByComparingTo("1700");
        json(
                "PUT",
                "/transactions/" + expense,
                Map.of(
                        "accountId",
                        source,
                        "categoryId",
                        category,
                        "amount",
                        75,
                        "currency",
                        "EUR",
                        "type",
                        "EXPENSE",
                        "date",
                        LocalDate.now().minusDays(2)),
                owner,
                200);
        assertThat(
                        json("GET", "/accounts/" + source, null, owner, 200)
                                .get("balance")
                                .decimalValue())
                .isEqualByComparingTo("1650");
        json("DELETE", "/transactions/" + transfer.get("id").asLong(), null, owner, 204);
        assertThat(
                        json("GET", "/accounts/" + source, null, owner, 200)
                                .get("balance")
                                .decimalValue())
                .isEqualByComparingTo("1850");
        assertThat(
                        json("GET", "/accounts/" + target, null, owner, 200)
                                .get("balance")
                                .decimalValue())
                .isEqualByComparingTo("500");
        json("DELETE", "/transactions/" + expense, null, owner, 204);
        assertThat(
                        json("GET", "/accounts/" + source, null, owner, 200)
                                .get("balance")
                                .decimalValue())
                .isEqualByComparingTo("2000");
    }
}
