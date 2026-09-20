package org.openfinance.service;

import static org.openfinance.service.EncryptedUserDataService.identifier;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManagerFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.openfinance.config.EncryptionProperties;
import org.openfinance.exception.BackupException;
import org.openfinance.security.EncryptionContext;
import org.openfinance.security.UserEncryptionLock;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.sqlite.SQLiteConnection;

/**
 * Copies committed WAL data, exports owned rows, and restores with fresh destination identifiers.
 */
@Service
@RequiredArgsConstructor
public class UserBackupArchiveImpl implements UserBackupArchive {
    // Parent-first insertion; restore deletes in reverse order with deferred foreign keys.
    private static final List<String> TABLES =
            List.of(
                    "institutions",
                    "categories",
                    "accounts",
                    "account_currency_changes",
                    "account_status_history",
                    "assets",
                    "liabilities",
                    "real_estate_properties",
                    "liability_tranches",
                    "payees",
                    "budgets",
                    "transactions",
                    "transactions_archive",
                    "transaction_splits",
                    "liability_principal_allocations",
                    "liability_asset_links",
                    "recurring_transactions",
                    "interest_rate_variations",
                    "real_estate_value_history",
                    "budget_alerts",
                    "transaction_rules",
                    "transaction_rule_conditions",
                    "transaction_rule_actions",
                    "net_worth",
                    "insights",
                    "ai_conversations",
                    "real_estate_simulations",
                    "attachments",
                    "operation_history",
                    "import_sessions",
                    "user_settings",
                    "security_audit_log");
    private static final Map<String, String> CHILDREN =
            Map.of(
                    "transaction_splits", "transaction_id:transactions",
                    "interest_rate_variations", "account_id:accounts",
                    "budget_alerts", "budget_id:budgets",
                    "transaction_rule_conditions", "rule_id:transaction_rules",
                    "transaction_rule_actions", "rule_id:transaction_rules");
    private static final Map<String, String> REFERENCES =
            Map.ofEntries(
                    Map.entry("user_id", "users"),
                    Map.entry("account_id", "accounts"),
                    Map.entry("represented_by_account_id", "accounts"),
                    Map.entry("to_account_id", "accounts"),
                    Map.entry("institution_id", "institutions"),
                    Map.entry("currency_id", "currencies"),
                    Map.entry("category_id", "categories"),
                    Map.entry("parent_id", "categories"),
                    Map.entry("asset_id", "assets"),
                    Map.entry("liability_id", "liabilities"),
                    Map.entry("mortgage_id", "liabilities"),
                    Map.entry("tranche_id", "liability_tranches"),
                    Map.entry("source_tranche_id", "liability_tranches"),
                    Map.entry("real_estate_id", "real_estate_properties"),
                    Map.entry("property_id", "real_estate_properties"),
                    Map.entry("budget_id", "budgets"),
                    Map.entry("transaction_id", "transactions"),
                    Map.entry("rule_id", "transaction_rules"),
                    Map.entry("payee_id", "payees"));
    private static final Map<String, String> ENTITIES =
            Map.of(
                    "ACCOUNT",
                    "accounts",
                    "TRANSACTION",
                    "transactions",
                    "ASSET",
                    "assets",
                    "LIABILITY",
                    "liabilities",
                    "REAL_ESTATE",
                    "real_estate_properties",
                    "BUDGET",
                    "budgets",
                    "CATEGORY",
                    "categories",
                    "RECURRING_TRANSACTION",
                    "recurring_transactions");
    private static final List<String> PROFILE =
            List.of("base_currency", "secondary_currency", "profile_image", "onboarding_complete");
    private final DataSource dataSource;
    private final JdbcTemplate jdbc;
    private final EncryptionProperties encryptionProperties;
    private final EncryptedUserDataService encryptedData;
    private final MasterPasswordService masterPasswords;
    private final UserEncryptionLock lock;
    private final PlatformTransactionManager transactionManager;
    private final EntityManagerFactory entityManagerFactory;
    private final CacheManager cacheManager;
    private final ObjectMapper objectMapper;

    private static String owned(String table) {
        String child = CHILDREN.get(table);
        if (child == null) return "user_id = ?";
        String[] parts = child.split(":");
        return identifier(parts[0])
                + " IN (SELECT id FROM "
                + identifier(parts[1])
                + " WHERE user_id = ?)";
    }

    @Override
    public void write(Long userId, Path target) throws IOException {
        Path snapshot = Files.createTempFile("openfinance-snapshot-", ".db");
        try (UserEncryptionLock.Scope ignored = lock.acquire(userId, false)) {
            try (Connection source = dataSource.getConnection()) {
                if (!source.isWrapperFor(SQLiteConnection.class))
                    throw BackupException.validation("User backups require SQLite");
                int result =
                        source.unwrap(SQLiteConnection.class)
                                .getDatabase()
                                .backup("main", snapshot.toString(), null);
                if (result != 0) throw new SQLException("SQLite snapshot failed: " + result);
            }
            try (Connection source = DriverManager.getConnection("jdbc:sqlite:" + snapshot);
                    Connection destination = DriverManager.getConnection("jdbc:sqlite:" + target)) {
                JdbcTemplate sourceJdbc = template(source);
                JdbcTemplate archive = template(destination);
                destination.setAutoCommit(false);
                Map<String, Object> user =
                        sourceJdbc.queryForMap("SELECT * FROM users WHERE id = ?", userId);
                archive.execute(
                        "CREATE TABLE backup_manifest (format_version INTEGER, schema_version TEXT, source_user_id INTEGER, encrypted INTEGER, salt TEXT, verifier TEXT, source_username TEXT, source_email TEXT)");
                archive.update(
                        "INSERT INTO backup_manifest VALUES (1, ?, ?, ?, ?, ?, ?, ?)",
                        schemaVersion(sourceJdbc),
                        userId,
                        encryptionProperties.isEnabled(),
                        user.get("master_password_salt"),
                        user.get("master_password_verifier"),
                        user.get("username"),
                        user.get("email"));
                copyTable(
                        sourceJdbc,
                        archive,
                        "user_profile",
                        "SELECT base_currency, secondary_currency, profile_image, onboarding_complete FROM users WHERE id = ?",
                        userId);
                copyTable(sourceJdbc, archive, "currencies", "SELECT * FROM currencies");
                for (String table : TABLES) {
                    String condition = owned(table);
                    Object[] owners = {userId};
                    if (table.equals("institutions")) {
                        condition +=
                                " OR (user_id IS NULL AND id IN (SELECT institution_id FROM accounts WHERE user_id = ? UNION SELECT institution_id FROM liabilities WHERE user_id = ?))";
                        owners = new Object[] {userId, userId, userId};
                    }
                    copyTable(
                            sourceJdbc,
                            archive,
                            table,
                            "SELECT * FROM " + identifier(table) + " WHERE " + condition,
                            owners);
                }
                destination.commit();
            }
        } catch (SQLException ex) {
            throw new IOException("Could not create user archive", ex);
        } finally {
            Files.deleteIfExists(snapshot);
        }
    }

    private static JdbcTemplate template(Connection connection) {
        return new JdbcTemplate(new SingleConnectionDataSource(connection, true));
    }

    private static String schemaVersion(JdbcTemplate database) {
        return database.queryForObject(
                "SELECT version FROM flyway_schema_history WHERE success = 1 AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1",
                String.class);
    }

    private static void copyTable(
            JdbcTemplate source, JdbcTemplate target, String table, String sql, Object... args) {
        source.query(
                sql,
                rs -> {
                    List<String> columns = new ArrayList<>();
                    for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++)
                        columns.add(rs.getMetaData().getColumnName(i));
                    target.execute(
                            "CREATE TABLE "
                                    + identifier(table)
                                    + " ("
                                    + String.join(
                                            ",",
                                            columns.stream()
                                                    .map(c -> identifier(c) + " BLOB")
                                                    .toList())
                                    + ")");
                    String insert = insertSql(table, columns);
                    while (rs.next()) {
                        Object[] values = new Object[columns.size()];
                        for (int i = 0; i < values.length; i++) values[i] = rs.getObject(i + 1);
                        target.update(insert, values);
                    }
                    return null;
                },
                args);
    }

    private static String insertSql(String table, List<String> columns) {
        return "INSERT INTO "
                + identifier(table)
                + " ("
                + String.join(
                        ",", columns.stream().map(EncryptedUserDataService::identifier).toList())
                + ") VALUES ("
                + String.join(",", Collections.nCopies(columns.size(), "?"))
                + ")";
    }

    @Override
    public void validateDownload(Long userId, Path source) throws IOException {
        try (Connection connection =
                DriverManager.getConnection(
                        "jdbc:sqlite:file:" + source.toAbsolutePath() + "?mode=ro")) {
            try (java.sql.Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA trusted_schema = OFF");
            }
            JdbcTemplate archive = template(connection);
            validateTables(archive);
            Long sourceUser =
                    archive.queryForObject(
                            "SELECT source_user_id FROM backup_manifest", Long.class);
            if (!userId.equals(sourceUser))
                throw BackupException.validation("Backup belongs to a different user");
            readAndValidate(archive, userId);
        } catch (BackupException ex) {
            throw ex;
        } catch (Exception ex) {
            throw BackupException.validation("This file is not a portable user backup", ex);
        }
    }

    @Override
    public void restore(Long userId, Path source, String masterPassword) throws IOException {
        try (UserEncryptionLock.Scope ignored = lock.acquire(userId, true);
                Connection connection =
                        DriverManager.getConnection(
                                "jdbc:sqlite:file:" + source.toAbsolutePath() + "?mode=ro")) {
            try (java.sql.Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA trusted_schema = OFF");
            }
            JdbcTemplate archive = template(connection);
            validateTables(archive);
            Map<String, Object> manifest = archive.queryForMap("SELECT * FROM backup_manifest");
            if (((Number) manifest.get("format_version")).intValue() != 1
                    || !schemaVersion(jdbc).equals(manifest.get("schema_version"))) {
                throw BackupException.validation(
                        "Backup format or schema version is incompatible with this instance");
            }
            SecretKey targetKey =
                    encryptionProperties.isEnabled() ? EncryptionContext.getKey() : null;
            if (encryptionProperties.isEnabled() && targetKey == null)
                throw BackupException.validation("Destination encryption session required");
            SecretKey sourceKey = sourceKey(manifest, masterPassword, targetKey);
            long sourceUser = ((Number) manifest.get("source_user_id")).longValue();
            Map<String, List<Map<String, Object>>> data = readAndValidate(archive, sourceUser);
            new TransactionTemplate(transactionManager)
                    .executeWithoutResult(
                            status -> {
                                jdbc.update(
                                        "UPDATE users SET updated_at = updated_at WHERE id = ?",
                                        userId);
                                jdbc.execute("PRAGMA defer_foreign_keys = ON");
                                Map<String, Map<Long, Long>> ids =
                                        allocateIds(data, userId, sourceUser);
                                assertNoForeignDependents(userId);
                                jdbc.update("DELETE FROM search_tokens WHERE user_id = ?", userId);
                                List<String> reverse = new ArrayList<>(TABLES);
                                Collections.reverse(reverse);
                                for (String table : reverse)
                                    jdbc.update(
                                            "DELETE FROM "
                                                    + identifier(table)
                                                    + " WHERE "
                                                    + owned(table),
                                            userId);
                                Map<String, String> transfers = new LinkedHashMap<>();
                                for (String table : TABLES) {
                                    for (Map<String, Object> row : data.get(table)) {
                                        if (table.equals("institutions")
                                                && existingSharedInstitution(row) != null) continue;

                                        // Shared institutions are copied as private rows,
                                        // preserving their source details.
                                        Map<String, Object> converted =
                                                convertRow(
                                                        table, row, ids, userId, sourceKey,
                                                        targetKey, transfers);
                                        jdbc.update(
                                                insertSql(
                                                        table, new ArrayList<>(converted.keySet())),
                                                converted.values().toArray());
                                    }
                                }
                                Map<String, Object> profile =
                                        archive.queryForMap("SELECT * FROM user_profile");
                                List<Object> values = new ArrayList<>();
                                for (String column : PROFILE) values.add(profile.get(column));
                                values.add(userId);
                                jdbc.update(
                                        "UPDATE users SET "
                                                + String.join(
                                                        ",",
                                                        PROFILE.stream()
                                                                .map(c -> identifier(c) + " = ?")
                                                                .toList())
                                                + " WHERE id = ?",
                                        values.toArray());
                                encryptedData.rebuildSearchTokens(userId, targetKey);
                                if (!jdbc.queryForList("PRAGMA foreign_key_check").isEmpty())
                                    throw BackupException.validation(
                                            "Backup contains invalid references");
                            });
            entityManagerFactory.getCache().evictAll();
            cacheManager
                    .getCacheNames()
                    .forEach(
                            name -> {
                                org.springframework.cache.Cache cache = cacheManager.getCache(name);
                                if (cache != null) cache.clear();
                            });
        } catch (BackupException ex) {
            throw ex;
        } catch (Exception ex) {
            throw BackupException.validation(
                    "Could not restore user backup; verify the file and its master password", ex);
        }
    }

    private SecretKey sourceKey(
            Map<String, Object> manifest, String password, SecretKey currentKey) {
        if (((Number) manifest.get("encrypted")).intValue() == 0) return null;
        String verifier = (String) manifest.get("verifier");
        if (password != null && !password.isBlank()) {
            SecretKey key = masterPasswords.derive(password, (String) manifest.get("salt"));
            if (verifier != null) masterPasswords.verifySentinel(verifier, key);
            return key;
        }
        if (currentKey != null && verifier != null) {
            try {
                masterPasswords.verifySentinel(verifier, currentKey);
                return currentKey;
            } catch (RuntimeException ignored) {
                // A portable archive from another account uses a different salt/key.
            }
        }
        throw BackupException.validation(
                "Enter the master password used when this backup was created");
    }

    private static void validateTables(JdbcTemplate archive) {
        List<String> expected = new ArrayList<>(TABLES);
        expected.addAll(List.of("backup_manifest", "user_profile", "currencies"));
        List<Map<String, Object>> objects =
                archive.queryForList(
                        "SELECT name, type FROM sqlite_master WHERE name NOT LIKE 'sqlite_%'");
        if (objects.size() != expected.size()
                || objects.stream()
                        .anyMatch(
                                row ->
                                        !"table".equals(row.get("type"))
                                                || !expected.contains(row.get("name")))) {
            throw BackupException.validation(
                    "Expected a portable user backup; whole-instance database files cannot be imported");
        }
    }

    private Map<String, List<Map<String, Object>>> readAndValidate(
            JdbcTemplate archive, long sourceUser) {
        Map<String, List<Map<String, Object>>> data = new LinkedHashMap<>();
        for (String table : TABLES) {
            List<String> expected =
                    jdbc.queryForList("PRAGMA table_info(" + identifier(table) + ")").stream()
                            .map(c -> c.get("name").toString())
                            .toList();
            List<String> actual =
                    archive.queryForList("PRAGMA table_info(" + identifier(table) + ")").stream()
                            .map(c -> c.get("name").toString())
                            .toList();
            if (!expected.equals(actual))
                throw BackupException.validation("Backup columns are incompatible: " + table);
            List<Map<String, Object>> rows =
                    archive.queryForList("SELECT * FROM " + identifier(table));
            for (Map<String, Object> row : rows) {
                Object owner = row.get("user_id");
                if (actual.contains("user_id")
                        && !(table.equals("institutions") && owner == null)
                        && (!(owner instanceof Number)
                                || ((Number) owner).longValue() != sourceUser)) {
                    throw BackupException.validation("Backup contains data from another user");
                }
            }
            data.put(table, rows);
        }
        data.put("currencies", archive.queryForList("SELECT * FROM currencies"));
        data.put("user_profile", archive.queryForList("SELECT * FROM user_profile"));
        return data;
    }

    private Map<String, Map<Long, Long>> allocateIds(
            Map<String, List<Map<String, Object>>> data, Long userId, long sourceUser) {
        Map<String, Map<Long, Long>> ids = new LinkedHashMap<>();
        ids.put("users", Map.of(sourceUser, userId));
        ids.put("currencies", mapCurrencies(data));
        for (String table : TABLES) {
            long next =
                    jdbc.queryForObject(
                            "SELECT COALESCE(MAX(id), 0) FROM " + identifier(table), Long.class);
            if (table.equals("transactions"))
                next =
                        Math.max(
                                next,
                                jdbc.queryForObject(
                                        "SELECT COALESCE(MAX(id), 0) FROM transactions_archive",
                                        Long.class));
            Map<Long, Long> mapping = new LinkedHashMap<>();
            if (table.equals("transactions_archive")) {
                mapping = ids.get("transactions");
                next =
                        Math.max(
                                next,
                                mapping.values().stream()
                                        .mapToLong(Long::longValue)
                                        .max()
                                        .orElse(0));
            }
            for (Map<String, Object> row : data.get(table)) {
                long old = ((Number) row.get("id")).longValue();
                Long shared = table.equals("institutions") ? existingSharedInstitution(row) : null;
                long destination = shared == null ? ++next : shared;
                if (old <= 0 || mapping.putIfAbsent(old, destination) != null)
                    throw BackupException.validation("Duplicate record identifier in backup");
            }
            ids.put(table, mapping);
        }
        return ids;
    }

    private Map<Long, Long> mapCurrencies(Map<String, List<Map<String, Object>>> data) {
        Map<Long, Long> ids = new LinkedHashMap<>();
        for (Map<String, Object> row : data.get("currencies")) {
            List<Long> matches =
                    jdbc.queryForList(
                            "SELECT id FROM currencies WHERE code = ?",
                            Long.class,
                            row.get("code"));
            if (matches.isEmpty()) {
                if (!currencyNeeded(data, row)) continue;
                jdbc.update(
                        "INSERT INTO currencies (code, name, symbol, is_active, name_key, type) VALUES (?, ?, ?, ?, ?, ?)",
                        row.get("code"),
                        row.get("name"),
                        row.get("symbol"),
                        row.get("is_active"),
                        row.get("name_key"),
                        row.get("type"));
                matches =
                        jdbc.queryForList(
                                "SELECT id FROM currencies WHERE code = ?",
                                Long.class,
                                row.get("code"));
            }
            ids.put(((Number) row.get("id")).longValue(), matches.getFirst());
        }
        return ids;
    }

    private static boolean currencyNeeded(
            Map<String, List<Map<String, Object>>> data, Map<String, Object> currency) {
        for (List<Map<String, Object>> records : data.values()) {
            for (Map<String, Object> record : records) {
                Object id = record.get("currency_id");
                if (id instanceof Number number
                        && number.longValue() == ((Number) currency.get("id")).longValue())
                    return true;
                for (String field :
                        List.of(
                                "currency",
                                "original_currency",
                                "base_currency",
                                "secondary_currency")) {
                    if (java.util.Objects.equals(record.get(field), currency.get("code")))
                        return true;
                }
            }
        }
        return false;
    }

    private Long existingSharedInstitution(Map<String, Object> row) {
        if (row.get("user_id") != null) return null;
        List<Long> matches =
                jdbc.queryForList(
                        "SELECT id FROM institutions WHERE user_id IS NULL AND name = ? AND bic IS ? AND country IS ? ORDER BY id LIMIT 1",
                        Long.class,
                        row.get("name"),
                        row.get("bic"),
                        row.get("country"));
        return matches.isEmpty() ? null : matches.getFirst();
    }

    private void assertNoForeignDependents(Long userId) {
        for (String table : TABLES) {
            List<String> columns =
                    jdbc.queryForList("PRAGMA table_info(" + identifier(table) + ")").stream()
                            .map(c -> c.get("name").toString())
                            .toList();
            for (Map.Entry<String, String> ref : REFERENCES.entrySet()) {
                if (!columns.contains(ref.getKey())
                        || !TABLES.contains(ref.getValue())
                        || CHILDREN.containsKey(ref.getValue())) continue;
                Integer count =
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM "
                                        + identifier(table)
                                        + " WHERE NOT ("
                                        + owned(table)
                                        + ") AND "
                                        + identifier(ref.getKey())
                                        + " IN (SELECT id FROM "
                                        + identifier(ref.getValue())
                                        + " WHERE user_id = ?)",
                                Integer.class,
                                userId,
                                userId);
                if (count != null && count > 0)
                    throw BackupException.validation(
                            "Existing data contains cross-user references; restore cancelled");
            }
        }
    }

    private Map<String, Object> convertRow(
            String table,
            Map<String, Object> row,
            Map<String, Map<Long, Long>> ids,
            Long userId,
            SecretKey sourceKey,
            SecretKey targetKey,
            Map<String, String> transfers) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            String column = entry.getKey();
            Object value = entry.getValue();
            if (column.equals("id")) value = mapped(ids, table, value, false);
            else if (column.equals("user_id")) value = userId;
            else if (column.equals("entity_id"))
                value =
                        mapped(
                                ids,
                                ENTITIES.get(row.get("entity_type")),
                                value,
                                table.equals("operation_history") || table.equals("attachments"));
            else if (REFERENCES.containsKey(column))
                value = mapped(ids, REFERENCES.get(column), value, false);
            else if (column.equals("transfer_id") && value != null)
                value =
                        transfers.computeIfAbsent(
                                value.toString(), key -> UUID.randomUUID().toString());
            if (table.equals("institutions") && column.equals("is_system")) value = 0;
            if (table.equals("import_sessions")
                    && column.equals("status")
                    && List.of("PENDING", "PARSING", "PARSED", "REVIEWING", "IMPORTING")
                            .contains(value)) value = "FAILED";
            if (table.equals("operation_history")
                    && (column.equals("entity_snapshot_json")
                            || column.equals("changed_fields_json"))
                    && value != null) {
                String plain = encryptedData.plaintext(value.toString(), sourceKey);
                value = remapHistory(plain, ENTITIES.get(row.get("entity_type")), ids);
                result.put(column, encryptedData.translate(table, column, value, null, targetKey));
            } else
                result.put(
                        column,
                        encryptedData.translate(table, column, value, sourceKey, targetKey));
        }
        return result;
    }

    private static Object mapped(
            Map<String, Map<Long, Long>> ids, String table, Object value, boolean historical) {
        if (value == null) return null;
        if (table == null || !ids.containsKey(table) || !(value instanceof Number))
            throw BackupException.validation("Invalid backup reference");
        Long mapped = ids.get(table).get(((Number) value).longValue());
        // Missing historical entities get a negative tombstone ID, never a live destination ID.
        if (mapped == null && historical) return -Math.abs(((Number) value).longValue());
        if (mapped == null)
            throw BackupException.validation(
                    "Backup references an entity outside this user's data: " + table);
        return mapped;
    }

    private String remapHistory(String json, String entityTable, Map<String, Map<Long, Long>> ids) {
        try {
            JsonNode node = objectMapper.readTree(json);
            remapNode(node, entityTable, ids);
            return objectMapper.writeValueAsString(node);
        } catch (IOException ex) {
            throw BackupException.validation("Invalid history in backup", ex);
        }
    }

    private void remapNode(JsonNode node, String entityTable, Map<String, Map<Long, Long>> ids) {
        if (node instanceof ObjectNode object) {
            List<String> fields = new ArrayList<>();
            object.fieldNames().forEachRemaining(fields::add);
            for (String field : fields) {
                JsonNode value = object.get(field);
                String column =
                        field.replaceAll("([a-z])([A-Z])", "$1_$2")
                                .toLowerCase(java.util.Locale.ROOT);
                String table = field.equals("id") ? entityTable : REFERENCES.get(column);
                if (table != null && value.isIntegralNumber())
                    object.put(
                            field,
                            ((Number) mapped(ids, table, value.longValue(), true)).longValue());
                else remapNode(value, entityTable, ids);
            }
        } else if (node.isArray()) node.forEach(child -> remapNode(child, entityTable, ids));
    }
}
