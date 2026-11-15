package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDate;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openfinance.config.TestDatabaseConfig;
import org.openfinance.dto.LoginRequest;
import org.openfinance.dto.LoginResponse;
import org.openfinance.dto.UserRegistrationRequest;
import org.openfinance.entity.Account;
import org.openfinance.entity.AccountType;
import org.openfinance.entity.User;
import org.openfinance.exception.BackupException;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.UserRepository;
import org.openfinance.security.EncryptionContext;
import org.openfinance.security.EncryptionKeyCache;
import org.openfinance.util.DatabaseCleanupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@Import(TestDatabaseConfig.class)
@ActiveProfiles("test")
@DisplayName("Portable backups validate untrusted files and preserve live data on failure")
class UserBackupArchiveValidationTest {
    @Autowired private UserBackupArchive archives;
    @Autowired private UserService users;
    @Autowired private AuthService authentication;
    @Autowired private UserRepository userRepository;
    @Autowired private AccountRepository accounts;
    @Autowired private EncryptionKeyCache keys;
    @Autowired private DatabaseCleanupService cleanup;
    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbc;
    @TempDir Path directory;
    private Long userId;
    private Long accountId;
    private SecretKey key;
    private Path archive;

    @BeforeEach
    void portableArchive() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assumeTrue(connection.getMetaData().getDatabaseProductName().equals("SQLite"));
        }
        cleanup.execute();
        jdbc.update("DELETE FROM currencies WHERE code = 'XTEST'");
        users.registerUser(
                UserRegistrationRequest.builder()
                        .username("archivist")
                        .email("archivist@example.com")
                        .password("Password123!")
                        .masterPassword("Master123!")
                        .skipSeeding(true)
                        .build());
        User user = userRepository.findByUsername("archivist").orElseThrow();
        userId = user.getId();
        LoginResponse login =
                authentication.login(
                        LoginRequest.builder()
                                .username("archivist")
                                .password("Password123!")
                                .masterPassword("Master123!")
                                .build());
        key = keys.getKeyBySessionToken(login.getEncryptionKey()).orElseThrow();
        EncryptionContext.setKey(key);
        accountId =
                accounts.save(
                                Account.builder()
                                        .userId(userId)
                                        .name("Original account")
                                        .type(AccountType.CHECKING)
                                        .currency("USD")
                                        .balance(new BigDecimal("123.45"))
                                        .openingBalance(new BigDecimal("123.45"))
                                        .openingDate(LocalDate.of(2024, 1, 1))
                                        .isActive(true)
                                        .build())
                        .getId();
        archive = directory.resolve("portable.db");
        archives.write(userId, archive);
    }

    @AfterEach
    void clearKey() {
        EncryptionContext.clear();
    }

    private void editArchive(String sql, Object... arguments) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + archive)) {
            new JdbcTemplate(new SingleConnectionDataSource(connection, true))
                    .update(sql, arguments);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"private", "shared-new", "shared-existing"})
    void institutionRestorationReusesOnlyMatchingSharedRows(String kind) throws Exception {
        String name = "Archive bank " + java.util.UUID.randomUUID();
        Long existing = null;
        if (kind.equals("shared-existing")) {
            jdbc.update(
                    "INSERT INTO institutions (name, country, bic, is_system, user_id) VALUES (?, 'FR', 'TESTBANK', 1, NULL)",
                    name);
            existing =
                    jdbc.queryForObject(
                            "SELECT id FROM institutions WHERE name = ?", Long.class, name);
        }
        editArchive(
                "INSERT INTO institutions (id, name, country, bic, is_system, user_id, created_at, updated_at) VALUES (999999, ?, 'FR', 'TESTBANK', 1, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                name,
                kind.equals("private") ? userId : null);
        editArchive("UPDATE accounts SET institution_id = 999999");
        archives.restore(userId, archive, "Master123!");
        Account account = accounts.findByUserId(userId).getFirst();
        if (existing != null) {
            assertThat(account.getInstitution().getId()).isEqualTo(existing);
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT user_id FROM institutions WHERE id = ?",
                                    Long.class,
                                    existing))
                    .isNull();
        } else {
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT user_id FROM institutions WHERE id = ?",
                                    Long.class,
                                    account.getInstitution().getId()))
                    .isEqualTo(userId);
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT is_system FROM institutions WHERE id = ?",
                                    Integer.class,
                                    account.getInstitution().getId()))
                    .isZero();
        }
        assertThat(account.getBalance()).isEqualByComparingTo("123.45");
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "PARSING", "PARSED", "REVIEWING", "IMPORTING", "COMPLETED"})
    void restoreDoesNotResumeAnImportWhoseWorkerAndTemporaryFilesWereNotRestored(String status)
            throws Exception {
        editArchive(
                "INSERT INTO import_sessions (id, upload_id, user_id, file_name, status, account_id, total_transactions, imported_count, error_count, duplicate_count, skipped_count, confirmation_started, created_at, updated_at) VALUES (999998, 'old-upload', ?, 'statement.qif', ?, ?, 0, 0, 0, 0, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                userId,
                status,
                accountId);
        archives.restore(userId, archive, "Master123!");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM import_sessions WHERE user_id = ?",
                                String.class,
                                userId))
                .isEqualTo(status.equals("COMPLETED") ? "COMPLETED" : "FAILED");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT account_id FROM import_sessions WHERE user_id = ?",
                                Long.class,
                                userId))
                .isEqualTo(accounts.findByUserId(userId).getFirst().getId());
    }

    private void assertOriginalAccount() {
        EncryptionContext.setKey(key);
        assertThat(accounts.findByUserId(userId))
                .singleElement()
                .satisfies(
                        account -> {
                            assertThat(account.getId()).isEqualTo(accountId);
                            assertThat(account.getName()).isEqualTo("Original account");
                            assertThat(account.getBalance()).isEqualByComparingTo("123.45");
                        });
    }

    @Test
    void downloadsCannotReadAnotherUsersPortableArchive() {
        assertThatThrownBy(() -> archives.validateDownload(userId + 1, archive))
                .isInstanceOf(BackupException.class)
                .hasMessageContaining("different user");
        assertOriginalAccount();
    }

    @ParameterizedTest
    @ValueSource(strings = {"extra-table", "missing-table", "renamed-table", "view"})
    void rejectsUnexpectedDatabaseObjectsBeforeReadingTheirContents(String alteration)
            throws Exception {
        switch (alteration) {
            case "extra-table" -> editArchive("CREATE TABLE unrelated (id INTEGER)");
            case "missing-table" -> editArchive("DROP TABLE accounts");
            case "renamed-table" -> editArchive("ALTER TABLE accounts RENAME TO unrelated");
            case "view" -> {
                editArchive("DROP TABLE accounts");
                editArchive("CREATE VIEW accounts AS SELECT 1 AS id");
            }
            default -> throw new AssertionError(alteration);
        }
        assertThatThrownBy(() -> archives.validateDownload(userId, archive))
                .isInstanceOf(BackupException.class)
                .hasMessageContaining("portable user backup");
        assertOriginalAccount();
    }

    @Test
    void rejectsAlteredColumnsEvenWhenAllTableNamesAreValid() throws Exception {
        editArchive("ALTER TABLE accounts ADD COLUMN unexpected BLOB");
        assertThatThrownBy(() -> archives.validateDownload(userId, archive))
                .isInstanceOf(BackupException.class)
                .hasMessageContaining("columns are incompatible: accounts");
        assertOriginalAccount();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"not-an-owner", "999999"})
    void rejectsAccountRowsWithoutTheDeclaredNumericOwner(String owner) throws Exception {
        editArchive(
                "UPDATE accounts SET user_id = ?",
                owner == null ? null : owner.equals("999999") ? 999999L : owner);
        assertThatThrownBy(() -> archives.validateDownload(userId, archive))
                .isInstanceOf(BackupException.class)
                .hasMessageContaining("another user");
        assertOriginalAccount();
    }

    @ParameterizedTest
    @ValueSource(strings = {"version", "schema"})
    void incompatibleVersionsCannotEraseLiveData(String field) throws Exception {
        editArchive(
                field.equals("version")
                        ? "UPDATE backup_manifest SET format_version = 99"
                        : "UPDATE backup_manifest SET schema_version = 'future-schema'");
        assertThatThrownBy(() -> archives.restore(userId, archive, "Master123!"))
                .isInstanceOf(BackupException.class)
                .hasMessageContaining("incompatible");
        assertOriginalAccount();
    }

    @Test
    void anEncryptedDestinationRequiresAnUnlockedSession() {
        EncryptionContext.clear();
        assertThatThrownBy(() -> archives.restore(userId, archive, "Master123!"))
                .isInstanceOf(BackupException.class)
                .hasMessageContaining("Destination encryption session required");
        assertOriginalAccount();
    }

    @Test
    void wrongBackupPasswordLeavesExistingEncryptedRowsReadable() {
        assertThatThrownBy(() -> archives.restore(userId, archive, "Wrong-master123!"))
                .isInstanceOf(BackupException.class);
        assertOriginalAccount();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void usesTheUnlockedKeyWhenTheBackupPasswordIsNotRepeated(String password) throws Exception {
        archives.restore(userId, archive, password);
        assertThat(accounts.findByUserId(userId))
                .singleElement()
                .satisfies(
                        account -> {
                            assertThat(account.getId()).isNotEqualTo(accountId);
                            assertThat(account.getName()).isEqualTo("Original account");
                            assertThat(account.getBalance()).isEqualByComparingTo("123.45");
                        });
    }

    @Test
    void aDifferentUnlockedKeyDoesNotBypassTheSourcePassword() {
        EncryptionContext.setKey(new SecretKeySpec(new byte[32], "AES"));
        assertThatThrownBy(() -> archives.restore(userId, archive, ""))
                .isInstanceOf(BackupException.class)
                .hasMessageContaining("Enter the master password");
        assertOriginalAccount();
    }

    @ParameterizedTest
    @ValueSource(strings = {"duplicate", "zero", "negative", "missing-reference"})
    void invalidIdentifiersRollBackTheEntireRestore(String invalid) throws Exception {
        switch (invalid) {
            case "duplicate" -> editArchive("INSERT INTO accounts SELECT * FROM accounts");
            case "zero" -> editArchive("UPDATE accounts SET id = 0");
            case "negative" -> editArchive("UPDATE accounts SET id = -1");
            case "missing-reference" -> editArchive("UPDATE accounts SET institution_id = 999999");
            default -> throw new AssertionError(invalid);
        }
        assertThatThrownBy(() -> archives.restore(userId, archive, "Master123!"))
                .isInstanceOf(BackupException.class);
        assertOriginalAccount();
    }

    @ParameterizedTest
    @ValueSource(strings = {"unused", "code", "profile"})
    void restoresMissingCurrencyDefinitionsOnlyWhenUsed(String reference) throws Exception {
        editArchive(
                "INSERT INTO currencies (id, code, name, symbol, is_active, type) VALUES (999999, 'XTEST', 'Test currency', 'XT', 1, 'FIAT')");
        if (reference.equals("code")) editArchive("UPDATE accounts SET currency = 'XTEST'");
        if (reference.equals("profile"))
            editArchive("UPDATE user_profile SET base_currency = 'XTEST'");
        archives.restore(userId, archive, "Master123!");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM currencies WHERE code = 'XTEST'", Long.class))
                .isEqualTo(reference.equals("unused") ? 0L : 1L);
        assertThat(accounts.findByUserId(userId))
                .singleElement()
                .satisfies(
                        account -> assertThat(account.getBalance()).isEqualByComparingTo("123.45"));
    }
}
