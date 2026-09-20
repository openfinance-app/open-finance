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
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
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
class AuditRemediationIntegrationTest {
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
                    Files.createTempFile(Path.of("target", "test-dbs"), "audit-wal-", ".db");
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

    @Test
    void portableBackupIncludesWalAndAttachmentsAndPreservesBothLoginIdentities() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assumeTrue(connection.getMetaData().getDatabaseProductName().equals("SQLite"));
        }
        long destinationAccount = account(other, "Do not disclose this other user", "EUR", 777);
        long sourceAccount = account(owner, "Portable confidential account", "EUR", 1000);
        jdbc.update(
                "INSERT INTO institutions (name, bic, country, is_system) VALUES (?, ?, ?, 1)",
                "Shared bank " + owner.id(),
                "SHAREDXX",
                "FR");
        long institution =
                jdbc.queryForObject(
                        "SELECT id FROM institutions WHERE name = ?",
                        Long.class,
                        "Shared bank " + owner.id());
        jdbc.update(
                "UPDATE accounts SET institution_id = ? WHERE id = ?", institution, sourceAccount);
        long category = category(owner, "Portable expenses");
        expense(owner, sourceAccount, category, LocalDate.now().minusYears(8), 50);
        // Legacy archive fixture: backup/key rotation must still preserve historical backups.
        jdbc.update(
                "INSERT INTO transactions_archive (id, user_id, account_id, transaction_type, amount, currency, category_id, transaction_date, description, created_at, account_amount, account_currency) "
                        + "SELECT id, user_id, account_id, transaction_type, amount, currency, category_id, transaction_date, description, created_at, account_amount, account_currency FROM transactions WHERE user_id = ?",
                owner.id());
        jdbc.update("DELETE FROM transactions WHERE user_id = ?", owner.id());
        jdbc.update(
                "INSERT INTO import_sessions (upload_id, user_id, file_name, account_id, status, created_at, updated_at) VALUES (?, ?, 'pending.qif', ?, 'REVIEWING', ?, ?)",
                UUID.randomUUID().toString(),
                owner.id(),
                sourceAccount,
                java.time.LocalDateTime.now().toString(),
                java.time.LocalDateTime.now().toString());
        byte[] receipt = "%PDF-1.4\nportable receipt\n%%EOF".getBytes(StandardCharsets.UTF_8);
        attachment(owner, sourceAccount, receipt);
        JsonNode backup;
        // Keep a reader open so the expense remains in WAL when the backup snapshot is taken.
        try (Connection reader = dataSource.getConnection()) {
            reader.setAutoCommit(false);
            try (java.sql.Statement statement = reader.createStatement();
                    java.sql.ResultSet ignored =
                            statement.executeQuery("SELECT COUNT(*) FROM accounts")) {
                ignored.next();
                expense(owner, sourceAccount, category, LocalDate.now(), 125);
                backup = json("POST", "/backup/create", Map.of(), owner, 201);
            }
            reader.rollback();
        }
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
        Path archive = temporary.resolve("portable.db");
        try (GZIPInputStream input = new GZIPInputStream(new java.io.ByteArrayInputStream(bytes))) {
            Files.copy(input, archive);
        }
        assertThat(new String(Files.readAllBytes(archive), StandardCharsets.ISO_8859_1))
                .doesNotContain(other.username(), "Do not disclose this other user");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + archive);
                java.sql.Statement statement = connection.createStatement()) {
            try (java.sql.ResultSet result =
                    statement.executeQuery("SELECT COUNT(*) FROM transactions")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(1);
            }
            try (java.sql.ResultSet result =
                    statement.executeQuery(
                            "SELECT COUNT(*) FROM sqlite_master WHERE name IN ('users','system_settings','backups')")) {
                result.next();
                assertThat(result.getInt(1)).isZero();
            }
        }
        MockMultipartFile file =
                new MockMultipartFile("file", "portable.ofbak", "application/gzip", bytes);
        mvc.perform(
                        authenticated(
                                multipart("/api/v1/backup/restore/upload")
                                        .file(file)
                                        .param("masterPassword", "WrongPassword123!"),
                                other))
                .andExpect(status().isBadRequest());
        assertThat(
                        json("GET", "/accounts/" + destinationAccount, null, other, 200)
                                .get("balance")
                                .decimalValue())
                .isEqualByComparingTo("777");
        mvc.perform(
                        authenticated(
                                multipart("/api/v1/backup/restore/upload")
                                        .file(file)
                                        .param("masterPassword", MASTER),
                                other))
                .andExpect(status().isOk());
        JsonNode accounts = json("GET", "/accounts", null, other, 200);
        assertThat(accounts.size()).isEqualTo(1);
        long importedId = accounts.get(0).get("id").asLong();
        assertThat(importedId).isNotIn(sourceAccount, destinationAccount);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT institution_id FROM accounts WHERE id = ?",
                                Long.class,
                                importedId))
                .isEqualTo(institution);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM institutions WHERE user_id = ?",
                                Integer.class,
                                other.id()))
                .isZero();
        assertThat(accounts.get(0).get("name").asText()).isEqualTo("Portable confidential account");
        assertThat(accounts.get(0).get("balance").decimalValue()).isEqualByComparingTo("825");
        assertThat(
                        json("GET", "/accounts/" + sourceAccount, null, owner, 200)
                                .get("balance")
                                .decimalValue())
                .isEqualByComparingTo("825");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM transactions_archive WHERE user_id = ? AND account_id = ?",
                                Integer.class,
                                other.id(),
                                importedId))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM import_sessions WHERE user_id = ? AND account_id = ?",
                                String.class,
                                other.id(),
                                importedId))
                .isEqualTo("FAILED");
        long importedAttachment =
                jdbc.queryForObject(
                        "SELECT id FROM attachments WHERE user_id = ?", Long.class, other.id());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT entity_id FROM attachments WHERE id = ?",
                                Long.class,
                                importedAttachment))
                .isEqualTo(importedId);
        assertThat(
                        mvc.perform(
                                        authenticated(
                                                get(
                                                        "/api/v1/attachments/"
                                                                + importedAttachment
                                                                + "/download"),
                                                other))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsByteArray())
                .isEqualTo(receipt);
        json(
                "POST",
                "/auth/login",
                Map.of(
                        "username",
                        other.username(),
                        "password",
                        "LoginPassword123!",
                        "masterPassword",
                        "DestinationMaster123!"),
                null,
                200);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM transactions WHERE user_id = ? AND account_id = ?",
                                Integer.class,
                                other.id(),
                                importedId))
                .isEqualTo(1);
    }

    @Test
    void legacyWholeDatabaseBackupsCannotBeDownloadedByTheirMetadataOwner() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assumeTrue(connection.getMetaData().getDatabaseProductName().equals("SQLite"));
        }
        Path snapshot = temporary.resolve("legacy.db");
        try (Connection connection = dataSource.getConnection()) {
            int code =
                    connection
                            .unwrap(org.sqlite.SQLiteConnection.class)
                            .getDatabase()
                            .backup("main", snapshot.toString(), null);
            assertThat(code).isZero();
        }
        Path file = temporary.resolve("legacy.ofbak");
        try (java.io.OutputStream output =
                new java.util.zip.GZIPOutputStream(Files.newOutputStream(file))) {
            Files.copy(snapshot, output);
        }
        jdbc.update(
                "INSERT INTO backups (user_id, filename, file_path, file_size, checksum, status, backup_type, created_at) VALUES (?, ?, ?, ?, ?, 'COMPLETED', 'MANUAL', ?)",
                owner.id(),
                "legacy.ofbak",
                file.toString(),
                Files.size(file),
                "0".repeat(64),
                java.time.LocalDateTime.now().toString());
        long backup =
                jdbc.queryForObject(
                        "SELECT id FROM backups WHERE file_path = ?", Long.class, file.toString());
        mvc.perform(authenticated(get("/api/v1/backup/" + backup + "/download"), owner))
                .andExpect(status().isBadRequest());
    }

    @Test
    void wrongMasterIsRejectedAndRotationReencryptsRecordsAndAttachments() throws Exception {
        long account = account(owner, "Secret account", "EUR", 1000);
        long category = category(owner, "Encrypted expenses");
        expense(owner, account, category, LocalDate.now(), 10);
        byte[] receipt = "%PDF-1.4\nrotation receipt\n%%EOF".getBytes(StandardCharsets.UTF_8);
        long attachment = attachment(owner, account, receipt);
        String before =
                jdbc.queryForObject(
                        "SELECT name FROM accounts WHERE id = ?", String.class, account);
        json(
                "POST",
                "/auth/login",
                Map.of(
                        "username",
                        owner.username(),
                        "password",
                        "LoginPassword123!",
                        "masterPassword",
                        "WrongMaster123!"),
                null,
                401);
        json(
                "PUT",
                "/users/me/master-password",
                Map.of(
                        "currentMasterPassword",
                        "WrongMaster123!",
                        "newMasterPassword",
                        "ReplacementMaster123!"),
                owner,
                401);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT name FROM accounts WHERE id = ?", String.class, account))
                .isEqualTo(before);
        JsonNode response =
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
        assertThat(keys.getKeyBySessionToken(owner.session())).isEmpty();
        Auth rotated =
                new Auth(
                        owner.id(),
                        owner.username(),
                        owner.token(),
                        response.get("encryptionKey").asText());
        assertThat(json("GET", "/accounts/" + account, null, rotated, 200).get("name").asText())
                .isEqualTo("Secret account");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT name FROM accounts WHERE id = ?", String.class, account))
                .isNotEqualTo(before);
        assertThat(
                        mvc.perform(
                                        authenticated(
                                                get(
                                                        "/api/v1/attachments/"
                                                                + attachment
                                                                + "/download"),
                                                rotated))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsByteArray())
                .isEqualTo(receipt);
        json(
                "POST",
                "/auth/login",
                Map.of(
                        "username",
                        owner.username(),
                        "password",
                        "LoginPassword123!",
                        "masterPassword",
                        MASTER),
                null,
                401);
        json(
                "POST",
                "/auth/login",
                Map.of(
                        "username",
                        owner.username(),
                        "password",
                        "LoginPassword123!",
                        "masterPassword",
                        "ReplacementMaster123!"),
                null,
                200);
    }

    @Test
    void rotationRollsBackAllRowsAndSaltWhenAnyCiphertextIsInvalid() throws Exception {
        long account = account(owner, "Rollback account", "EUR", 100);
        String name =
                jdbc.queryForObject(
                        "SELECT name FROM accounts WHERE id = ?", String.class, account);
        String salt =
                jdbc.queryForObject(
                        "SELECT master_password_salt FROM users WHERE id = ?",
                        String.class,
                        owner.id());
        jdbc.update(
                "UPDATE accounts SET description = ? WHERE id = ?",
                java.util.Base64.getEncoder().encodeToString(new byte[40]),
                account);
        mvc.perform(
                        authenticated(
                                request(HttpMethod.PUT, "/api/v1/users/me/master-password")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                mapper.writeValueAsBytes(
                                                        Map.of(
                                                                "currentMasterPassword",
                                                                MASTER,
                                                                "newMasterPassword",
                                                                "ReplacementMaster123!"))),
                                owner))
                .andExpect(status().isBadRequest());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT name FROM accounts WHERE id = ?", String.class, account))
                .isEqualTo(name);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT master_password_salt FROM users WHERE id = ?",
                                String.class,
                                owner.id()))
                .isEqualTo(salt);
        assertThat(keys.getKeyBySessionToken(owner.session())).isPresent();
    }

    @Test
    void historyIncludesEarlierMovementsAndSplitBudgetsCountOnlyAllocatedAmounts()
            throws Exception {
        long account = account(owner, "Accounting account", "EUR", 1000);
        long category = category(owner, "Groceries");
        expense(owner, account, category, LocalDate.now().minusMonths(2), 100);
        JsonNode history =
                json(
                        "GET",
                        "/accounts/" + account + "/balance-history?period=1M",
                        null,
                        owner,
                        200);
        assertThat(history.get(0).get("balance").decimalValue()).isEqualByComparingTo("900");
        LocalDate first = LocalDate.now().withDayOfMonth(1);
        JsonNode budget =
                json(
                        "POST",
                        "/budgets",
                        Map.of(
                                "categoryId",
                                category,
                                "amount",
                                200,
                                "currency",
                                "EUR",
                                "period",
                                "MONTHLY",
                                "rollover",
                                false,
                                "startDate",
                                first,
                                "endDate",
                                first.plusMonths(1).minusDays(1)),
                        owner,
                        201);
        json(
                "POST",
                "/transactions",
                Map.of(
                        "accountId",
                        account,
                        "categoryId",
                        category,
                        "type",
                        "EXPENSE",
                        "amount",
                        100,
                        "currency",
                        "EUR",
                        "date",
                        LocalDate.now(),
                        "splits",
                        List.of(
                                Map.of("categoryId", category, "amount", 60),
                                Map.of("amount", 40))),
                owner,
                201);
        JsonNode progress =
                json(
                        "GET",
                        "/budgets/" + budget.get("id").asLong() + "/progress",
                        null,
                        owner,
                        200);
        assertThat(progress.get("spent").decimalValue()).isEqualByComparingTo("60");
        long foreign = category(other, "Private foreign category");
        json(
                "POST",
                "/transactions",
                Map.of(
                        "accountId",
                        account,
                        "type",
                        "EXPENSE",
                        "amount",
                        10,
                        "currency",
                        "EUR",
                        "date",
                        LocalDate.now(),
                        "splits",
                        List.of(Map.of("categoryId", foreign, "amount", 10))),
                owner,
                404);
    }

    @Test
    void recurringPostingIsIdempotentAndAccountDeletionReversesSurvivingTransfer()
            throws Exception {
        long from = account(owner, "From", "EUR", 1000);
        long to = account(owner, "To", "EUR", 0);
        json(
                "POST",
                "/recurring-transactions",
                Map.of(
                        "accountId",
                        from,
                        "toAccountId",
                        to,
                        "type",
                        "TRANSFER",
                        "amount",
                        100,
                        "currency",
                        "EUR",
                        "description",
                        "Scheduled transfer",
                        "frequency",
                        "MONTHLY",
                        "nextOccurrence",
                        LocalDate.now()),
                owner,
                201);
        json("POST", "/recurring-transactions/process", null, owner, 200);
        json("POST", "/recurring-transactions/process", null, owner, 200);
        assertThat(json("GET", "/accounts/" + to, null, owner, 200).get("balance").decimalValue())
                .isEqualByComparingTo("100");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM transactions WHERE user_id = ?",
                                Integer.class,
                                owner.id()))
                .isEqualTo(2);
        mvc.perform(
                        authenticated(
                                request(
                                        HttpMethod.DELETE,
                                        "/api/v1/accounts/" + from + "/permanent"),
                                owner))
                .andExpect(status().isNoContent());
        assertThat(json("GET", "/accounts/" + to, null, owner, 200).get("balance").decimalValue())
                .isEqualByComparingTo("0");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM transactions WHERE user_id = ?",
                                Integer.class,
                                owner.id()))
                .isZero();
    }

    @Test
    void encryptedAsyncImportKeepsTheOwnersKeyOnItsWorkerThread() throws Exception {
        long account = account(owner, "Imported account", "EUR", 100);
        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "private.qif",
                        "application/octet-stream",
                        "!Type:Bank\nD09/08/2026\nT-12.50\nPPrivate Merchant\nMImported confidential purchase\n^\n"
                                .getBytes(StandardCharsets.UTF_8));
        String uploaded =
                mvc.perform(authenticated(multipart("/api/v1/import/upload").file(file), owner))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String uploadId = mapper.readTree(uploaded).get("uploadId").asText();
        long session =
                json(
                                "POST",
                                "/import/process",
                                Map.of(
                                        "uploadId",
                                        uploadId,
                                        "accountId",
                                        account,
                                        "fileName",
                                        "private.qif"),
                                owner,
                                200)
                        .get("id")
                        .asLong();
        json("GET", "/import/sessions/" + session + "/review", null, owner, 200);
        json(
                "POST",
                "/import/sessions/" + session + "/confirm",
                Map.of("accountId", account, "skipDuplicates", true),
                owner,
                202);
        org.awaitility.Awaitility.await()
                .atMost(java.time.Duration.ofSeconds(15))
                .untilAsserted(
                        () -> {
                            JsonNode state =
                                    json("GET", "/import/sessions/" + session, null, owner, 200);
                            assertThat(state.get("status").asText()).isEqualTo("COMPLETED");
                            assertThat(state.get("importedCount").asInt()).isEqualTo(1);
                        });
        assertThat(
                        json("GET", "/accounts/" + account, null, owner, 200)
                                .get("balance")
                                .decimalValue())
                .isEqualByComparingTo("87.50");
        String stored =
                jdbc.queryForObject(
                        "SELECT description FROM transactions WHERE user_id = ?",
                        String.class,
                        owner.id());
        assertThat(stored).doesNotContain("Imported confidential purchase");
    }

    @Test
    void directDisbursementUpdatesPropertyAndItsBackingAsset() throws Exception {
        JsonNode property =
                json(
                        "POST",
                        "/real-estate",
                        Map.of(
                                "name",
                                "Financed property",
                                "propertyType",
                                "RESIDENTIAL",
                                "address",
                                "Test street",
                                "purchasePrice",
                                100,
                                "currentValue",
                                100,
                                "currency",
                                "EUR",
                                "purchaseDate",
                                "2026-01-01"),
                        owner,
                        201);
        JsonNode loan =
                json(
                        "POST",
                        "/liabilities",
                        Map.of(
                                "name",
                                "Staged loan",
                                "type",
                                "MORTGAGE",
                                "principal",
                                1000,
                                "currentBalance",
                                0,
                                "interestRate",
                                0,
                                "currency",
                                "EUR",
                                "minimumPayment",
                                100,
                                "startDate",
                                "2026-01-01",
                                "endDate",
                                "2027-01-01"),
                        owner,
                        201);
        json(
                "POST",
                "/liabilities/" + loan.get("id").asLong() + "/disburse",
                Map.of(
                        "amount",
                        100,
                        "date",
                        LocalDate.now(),
                        "directRealEstateId",
                        property.get("id").asLong()),
                owner,
                200);
        long backingId =
                jdbc.queryForObject(
                        "SELECT asset_id FROM real_estate_properties WHERE id = ?",
                        Long.class,
                        property.get("id").asLong());
        JsonNode asset = json("GET", "/assets/" + backingId, null, owner, 200);
        assertThat(asset.get("currentPrice").decimalValue()).isEqualByComparingTo("100");
        assertThat(asset.get("purchasePrice").decimalValue()).isEqualByComparingTo("100");
    }

    @Test
    void exportReturnsContentAndUnsupportedHistoryActionsDoNotChangeStatus() throws Exception {
        long account = account(owner, "Downloadable account", "EUR", 100);
        byte[] export =
                mvc.perform(
                                authenticated(
                                        post("/api/v1/data/export")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content("{\"format\":\"JSON\"}"),
                                        owner))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray();
        assertThat(new String(export, StandardCharsets.UTF_8)).contains("Downloadable account");
        JsonNode update =
                json(
                        "PUT",
                        "/accounts/" + account,
                        Map.of(
                                "name",
                                "Updated account",
                                "type",
                                "CHECKING",
                                "currency",
                                "EUR",
                                "initialBalance",
                                100,
                                "openingDate",
                                LocalDate.now().minusMonths(6)),
                        owner,
                        200);
        assertThat(update.get("name").asText()).isEqualTo("Updated account");
        long history =
                jdbc.queryForObject(
                        "SELECT id FROM operation_history WHERE user_id = ? AND operation_type = 'UPDATE' ORDER BY id DESC LIMIT 1",
                        Long.class,
                        owner.id());
        json("POST", "/history/" + history + "/undo", null, owner, 400);
        json("POST", "/history/" + history + "/redo", null, owner, 400);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT undone_at FROM operation_history WHERE id = ?",
                                String.class,
                                history))
                .isNull();
        assertThat(json("GET", "/accounts/" + account, null, owner, 200).get("name").asText())
                .isEqualTo("Updated account");
    }
}
