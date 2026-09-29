package org.openfinance.service;

import static org.openfinance.service.EncryptedUserDataService.identifier;
import static org.openfinance.service.EncryptedUserDataService.looksEncrypted;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Table;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.SecretKey;
import org.openfinance.security.EncryptionService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Raw encrypted-column access shared by key rotation and portable user backups. */
@Service
public class EncryptedUserDataServiceImpl implements EncryptedUserDataService {
    private final JdbcTemplate jdbc;
    private final EncryptionService encryption;
    private final SearchTokenService searchTokens;
    private final Map<String, List<String>> columns = new LinkedHashMap<>();

    public EncryptedUserDataServiceImpl(
            JdbcTemplate jdbc,
            EncryptionService encryption,
            SearchTokenService searchTokens,
            EntityManagerFactory entityManagerFactory) {
        this.jdbc = jdbc;
        this.encryption = encryption;
        this.searchTokens = searchTokens;
        entityManagerFactory
                .getMetamodel()
                .getEntities()
                .forEach(
                        entity -> {
                            Table table = entity.getJavaType().getAnnotation(Table.class);
                            if (table == null) return;
                            List<String> encrypted = new ArrayList<>();
                            for (Field field : entity.getJavaType().getDeclaredFields()) {
                                Convert convert = field.getAnnotation(Convert.class);
                                if (convert != null
                                        && convert.converter()
                                                .getSimpleName()
                                                .startsWith("Encrypted")) {
                                    Column column = field.getAnnotation(Column.class);
                                    encrypted.add(
                                            column == null || column.name().isEmpty()
                                                    ? field.getName()
                                                            .replaceAll("([a-z])([A-Z])", "$1_$2")
                                                            .toLowerCase(java.util.Locale.ROOT)
                                                    : column.name());
                                }
                            }
                            if (!encrypted.isEmpty())
                                columns.put(table.name(), List.copyOf(encrypted));
                        });
        List<String> archiveColumns =
                jdbc.query(
                        "SELECT * FROM transactions_archive WHERE 1 = 0",
                        rs -> {
                            List<String> names = new ArrayList<>();
                            for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++)
                                names.add(rs.getMetaData().getColumnName(i));
                            return names;
                        });
        columns.put(
                "transactions_archive",
                columns.get("transactions").stream().filter(archiveColumns::contains).toList());
    }

    public boolean isEncryptedColumn(String table, String column) {
        return columns.getOrDefault(table, List.of()).contains(column);
    }

    private String ownerCondition(String table) {
        return table.equals("transaction_splits")
                ? "transaction_id IN (SELECT id FROM transactions WHERE user_id = ?)"
                : "user_id = ?";
    }

    private List<Map<String, Object>> rows(String table, Long userId) {
        return jdbc.queryForList(
                "SELECT * FROM " + identifier(table) + " WHERE " + ownerCondition(table), userId);
    }

    public String plaintext(String value, SecretKey key) {
        if (value == null || key == null || !looksEncrypted(value)) return value;
        return encryption.decrypt(value, key);
    }

    public Object translate(
            String table, String column, Object value, SecretKey sourceKey, SecretKey targetKey) {
        if (value == null) return null;
        if (table.equals("attachments") && column.equals("file_data")) {
            byte[] bytes = (byte[]) value;
            byte[] plain = sourceKey == null ? bytes : encryption.decryptBytes(bytes, sourceKey);
            return targetKey == null ? plain : encryption.encryptBytes(plain, targetKey);
        }
        if (!isEncryptedColumn(table, column)) return value;
        String plain = plaintext(value.toString(), sourceKey);
        return targetKey == null ? plain : encryption.encrypt(plain, targetKey);
    }

    /** Must run within the same transaction that changes the user's salt and verifier. */
    public void rotate(Long userId, SecretKey sourceKey, SecretKey targetKey) {
        for (Map.Entry<String, List<String>> table : columns.entrySet()) {
            for (Map<String, Object> row : rows(table.getKey(), userId)) {
                List<String> fields = new ArrayList<>(table.getValue());
                if (table.getKey().equals("attachments")) fields.add("file_data");
                List<Object> values = new ArrayList<>();
                for (String field : fields)
                    values.add(
                            translate(table.getKey(), field, row.get(field), sourceKey, targetKey));
                values.add(row.get("id"));
                values.add(userId);
                String assignments =
                        fields.stream()
                                .map(c -> identifier(c) + " = ?")
                                .collect(java.util.stream.Collectors.joining(", "));
                jdbc.update(
                        "UPDATE "
                                + identifier(table.getKey())
                                + " SET "
                                + assignments
                                + " WHERE id = ? AND "
                                + ownerCondition(table.getKey()),
                        values.toArray());
            }
        }
        rebuildSearchTokens(userId, targetKey);
    }

    public void rebuildSearchTokens(Long userId, SecretKey key) {
        jdbc.update("DELETE FROM search_tokens WHERE user_id = ?", userId);
        if (key == null) return;
        SecretKey searchKey = searchTokens.deriveSearchKey(key);
        SEARCH_FIELDS.forEach(
                (table, names) -> {
                    for (Map<String, Object> row : rows(table, userId)) {
                        indexSearchRow(userId, key, searchKey, names, row);
                    }
                });
    }

    private static final Map<String, List<String>> SEARCH_FIELDS =
            Map.of(
                    "accounts", List.of("ACCOUNT", "name", "description"),
                    "assets", List.of("ASSET", "name"),
                    "liabilities", List.of("LIABILITY", "name"),
                    "budgets", List.of("BUDGET", "notes"),
                    "payees", List.of("PAYEE", "name"),
                    "real_estate_properties", List.of("REAL_ESTATE", "name", "address"),
                    "recurring_transactions",
                            List.of("RECURRING_TRANSACTION", "description", "notes"),
                    "transactions",
                            List.of("TRANSACTION", "description", "notes", "tags", "payee"));

    public void refreshSearchTokens(
            Long userId, SecretKey key, Map<String, java.util.Set<Long>> changedIds) {
        SecretKey searchKey = key == null ? null : searchTokens.deriveSearchKey(key);
        changedIds.forEach(
                (table, changed) -> {
                    List<String> names = SEARCH_FIELDS.get(table);
                    if (names == null) return;
                    List<Long> ids = new ArrayList<>(changed);
                    for (int start = 0; start < ids.size(); start += 500) {
                        List<Long> batch = ids.subList(start, Math.min(ids.size(), start + 500));
                        String parameters =
                                String.join(",", java.util.Collections.nCopies(batch.size(), "?"));
                        List<Object> arguments = new ArrayList<>(List.of(userId, names.getFirst()));
                        arguments.addAll(batch);
                        jdbc.update(
                                "DELETE FROM search_tokens WHERE user_id = ? AND entity_type = ? AND entity_id IN ("
                                        + parameters
                                        + ")",
                                arguments.toArray());
                        if (key == null) continue;
                        arguments = new ArrayList<>(List.of(userId));
                        arguments.addAll(batch);
                        for (Map<String, Object> row :
                                jdbc.queryForList(
                                        "SELECT * FROM "
                                                + identifier(table)
                                                + " WHERE user_id = ? AND id IN ("
                                                + parameters
                                                + ")",
                                        arguments.toArray()))
                            indexSearchRow(userId, key, searchKey, names, row);
                    }
                });
    }

    private void indexSearchRow(
            Long userId,
            SecretKey key,
            SecretKey searchKey,
            List<String> names,
            Map<String, Object> row) {
        List<String[]> contents = new ArrayList<>();
        for (String name : names.subList(1, names.size())) {
            Object value = row.get(name);
            contents.add(
                    new String[] {name, value == null ? null : plaintext(value.toString(), key)});
        }
        searchTokens.indexEntity(
                userId,
                names.getFirst(),
                ((Number) row.get("id")).longValue(),
                contents,
                searchKey);
    }
}
