package org.openfinance.service.history;

import static org.openfinance.service.EncryptedUserDataService.identifier;
import static org.openfinance.service.history.HistoryDomainRegistry.TABLES;
import static org.openfinance.service.history.HistoryDomainRegistry.owned;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.openfinance.entity.EntityType;
import org.openfinance.security.EncryptionContext;
import org.openfinance.service.EncryptedUserDataService;
import org.openfinance.service.history.HistoryChangeSet.Change;
import org.openfinance.service.history.HistoryChangeSet.Guard;
import org.openfinance.service.history.HistoryChangeSet.Key;
import org.openfinance.service.history.HistoryChangeSet.Reference;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Restores explicitly captured financial rows with ownership, dependency and current-state guards.
 */
@Component
@lombok.extern.slf4j.Slf4j
@RequiredArgsConstructor
public class HistoryStateStore {
    private final JdbcTemplate jdbc;
    private final EncryptedUserDataService encryption;
    private final EntityManager entityManager;
    private boolean sqlite;

    @jakarta.annotation.PostConstruct
    void detectDatabase() {
        sqlite =
                Boolean.TRUE.equals(
                        jdbc.execute(
                                (org.springframework.jdbc.core.ConnectionCallback<Boolean>)
                                        connection ->
                                                connection
                                                        .getMetaData()
                                                        .getDatabaseProductName()
                                                        .equals("SQLite")));
    }

    private final Map<String, Map<String, Integer>> columnTypes = new ConcurrentHashMap<>();

    public record Snapshot(
            Map<Key, Map<String, Object>> rows, Map<Key, List<Reference>> references) {}

    public void lock(Long userId) {
        jdbc.update(
                "INSERT INTO operation_history_state (user_id, revision) VALUES (?, 0) ON CONFLICT (user_id) DO NOTHING",
                userId);
        jdbc.update(
                "UPDATE operation_history_state SET revision = revision + 1 WHERE user_id = ?",
                userId);
    }

    public int captureIsolation() {
        return sqlite
                ? org.springframework.transaction.TransactionDefinition.ISOLATION_SERIALIZABLE
                : org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ;
    }

    public Map<String, Integer> columns(String table) {
        if (!TABLES.contains(table))
            throw new IllegalArgumentException("Unsupported history table");
        return columnTypes.computeIfAbsent(
                table,
                name ->
                        jdbc.query(
                                "SELECT * FROM " + identifier(name) + " WHERE 1 = 0",
                                rs -> {
                                    Map<String, Integer> types = new LinkedHashMap<>();
                                    for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++)
                                        types.put(
                                                rs.getMetaData().getColumnName(i),
                                                rs.getMetaData().getColumnType(i));
                                    return types;
                                }));
    }

    public Snapshot snapshot(Long userId, Set<Long> attachmentIds) {
        entityManager.flush();
        Map<Key, Map<String, Object>> rows = new LinkedHashMap<>();
        for (String table : TABLES) {
            String fields =
                    columns(table).keySet().stream()
                            .filter(
                                    column ->
                                            !table.equals("attachments")
                                                    || !column.equals("file_data"))
                            .map(EncryptedUserDataService::identifier)
                            .collect(Collectors.joining(","));
            jdbc.queryForList(
                            "SELECT "
                                    + fields
                                    + " FROM "
                                    + identifier(table)
                                    + " WHERE "
                                    + owned(table)
                                    + " ORDER BY id",
                            userId)
                    .forEach(
                            row ->
                                    rows.put(
                                            new Key(table, ((Number) row.get("id")).longValue()),
                                            row));
        }
        for (Long id : attachmentIds) {
            Map<String, Object> row = rows.get(new Key("attachments", id));
            if (row != null)
                row.put(
                        "file_data",
                        jdbc.queryForObject(
                                "SELECT file_data FROM attachments WHERE id = ? AND user_id = ?",
                                byte[].class,
                                id,
                                userId));
        }
        return new Snapshot(rows, references(rows));
    }

    /** Load binary contents only for attachments the domain deletion can remove. */
    public Set<Long> deletionAttachments(EntityType type, Long id, Long userId) {
        if (id == null) return Set.of();
        Set<Long> ids =
                new LinkedHashSet<>(
                        jdbc.queryForList(
                                "SELECT id FROM attachments WHERE user_id = ? AND entity_type = ? AND entity_id = ?",
                                Long.class,
                                userId,
                                type.name(),
                                id));
        if (type == EntityType.REAL_ESTATE)
            ids.addAll(
                    jdbc.queryForList(
                            "SELECT id FROM attachments WHERE user_id = ? AND entity_type = 'ASSET' AND entity_id IN (SELECT asset_id FROM real_estate_properties WHERE id = ? AND user_id = ?)",
                            Long.class,
                            userId,
                            id,
                            userId));
        if (type == EntityType.TRANSACTION)
            ids.addAll(
                    jdbc.queryForList(
                            "SELECT id FROM attachments WHERE user_id = ? AND entity_type = 'TRANSACTION' AND entity_id IN (SELECT id FROM transactions WHERE user_id = ? AND transfer_id IN (SELECT transfer_id FROM transactions WHERE id = ? AND user_id = ?))",
                            Long.class,
                            userId,
                            userId,
                            id,
                            userId));
        return ids;
    }

    private Map<Key, List<Reference>> references(Map<Key, Map<String, Object>> rows) {
        Map<Key, List<Reference>> result = new LinkedHashMap<>();
        rows.forEach(
                (key, row) -> {
                    row.forEach(
                            (column, value) -> {
                                String parent = HistoryDomainRegistry.REFERENCES.get(column);
                                if (parent != null && value instanceof Number number)
                                    addReference(
                                            result,
                                            new Key(parent, number.longValue()),
                                            new Reference(key.table(), column, key.id()));
                            });
                    if (key.table().equals("attachments")
                            && row.get("entity_id") instanceof Number id) {
                        String parent =
                                HistoryDomainRegistry.ENTITIES.entrySet().stream()
                                        .filter(
                                                entry ->
                                                        entry.getKey()
                                                                .name()
                                                                .equals(row.get("entity_type")))
                                        .map(Map.Entry::getValue)
                                        .findFirst()
                                        .orElse(null);
                        if (parent != null)
                            addReference(
                                    result,
                                    new Key(parent, id.longValue()),
                                    new Reference(key.table(), "entity_id", key.id()));
                    }
                });
        return result;
    }

    private static void addReference(
            Map<Key, List<Reference>> references, Key key, Reference reference) {
        references.computeIfAbsent(key, ignored -> new ArrayList<>()).add(reference);
    }

    public HistoryChangeSet difference(Snapshot before, Snapshot after) {
        Set<Key> keys = new LinkedHashSet<>(before.rows().keySet());
        keys.addAll(after.rows().keySet());
        List<Change> changes = new ArrayList<>();
        List<Guard> guards = new ArrayList<>();
        for (Key key : keys) {
            Map<String, Object> oldRow = before.rows().get(key);
            Map<String, Object> newRow = after.rows().get(key);
            if (rawEqual(oldRow, newRow)) continue;
            if (key.table().equals("attachments")
                    && java.util.stream.Stream.of(oldRow, newRow)
                            .filter(Objects::nonNull)
                            .anyMatch(row -> !row.containsKey("file_data")))
                throw new IllegalStateException(
                        "Attachment changes require complete history capture");
            Map<String, String> oldState = plain(key.table(), oldRow);
            Map<String, String> newState = plain(key.table(), newRow);
            if (HistoryChangeSet.equal(oldState, newState, false)) continue;
            Change change = new Change(key.table(), key.id(), oldState, newState);
            changes.add(change);
            if (!change.balanceOnly())
                guards.add(
                        new Guard(
                                key.table(),
                                key.id(),
                                before.references().getOrDefault(key, List.of()),
                                after.references().getOrDefault(key, List.of())));
        }
        return new HistoryChangeSet(HistoryChangeSet.FORMAT, changes, guards);
    }

    private static boolean rawEqual(Map<String, Object> a, Map<String, Object> b) {
        if (a == null || b == null) return a == b;
        return a.keySet().equals(b.keySet())
                && a.entrySet().stream()
                        .allMatch(
                                entry ->
                                        Objects.deepEquals(
                                                entry.getValue(), b.get(entry.getKey())));
    }

    public Map<String, String> plain(String table, Map<String, Object> row) {
        if (row == null) return null;
        Map<String, String> result = new LinkedHashMap<>();
        row.forEach(
                (column, value) -> {
                    Object clear =
                            encryption.translate(
                                    table, column, value, EncryptionContext.getKey(), null);
                    result.put(
                            column,
                            clear == null
                                    ? null
                                    : clear instanceof byte[] bytes
                                            ? Base64.getEncoder().encodeToString(bytes)
                                            : clear.toString());
                });
        return result;
    }

    public String unavailable(
            HistoryChangeSet changeSet, Snapshot current, boolean redo, Long userId) {
        if (changeSet.format() != HistoryChangeSet.FORMAT || changeSet.changes().isEmpty())
            return "legacy";
        for (Change change : changeSet.changes()) {
            Map<String, String> expected = redo ? change.before() : change.after();
            if (!valid(change, userId)) return "legacy";
            Map<String, String> actual = plain(change.table(), current.rows().get(change.key()));
            if (!HistoryChangeSet.equal(expected, actual, change.additiveBalance())) {
                log.debug(
                        "History state conflict on {}/{} in columns {}",
                        change.table(),
                        change.id(),
                        expected == null || actual == null
                                ? "row existence"
                                : expected.keySet().stream()
                                        .filter(
                                                column ->
                                                        !HistoryChangeSet.TECHNICAL.contains(column)
                                                                && !(change.additiveBalance()
                                                                        && column.equals("balance"))
                                                                && !Objects.equals(
                                                                        expected.get(column),
                                                                        actual.get(column)))
                                        .toList());
                return "changed";
            }
        }
        for (Guard guard : changeSet.guards()) {
            List<Reference> expected = redo ? guard.before() : guard.after();
            List<Reference> actual =
                    current.references()
                            .getOrDefault(new Key(guard.table(), guard.id()), List.of());
            if (!new LinkedHashSet<>(expected).equals(new LinkedHashSet<>(actual)))
                return "dependencies";
        }
        return nameConflict(changeSet, current, redo) ? "dependencies" : null;
    }

    private boolean nameConflict(HistoryChangeSet changeSet, Snapshot current, boolean redo) {
        Map<Key, Map<String, String>> destinations = new LinkedHashMap<>();
        changeSet
                .changes()
                .forEach(
                        change ->
                                destinations.put(
                                        change.key(), redo ? change.after() : change.before()));
        for (Change change : changeSet.changes()) {
            if (!Set.of("payees", "categories").contains(change.table())) continue;
            Map<String, String> target = destinations.get(change.key());
            if (target == null || target.get("name") == null) continue;
            if (change.table().equals("payees")
                    && jdbc.queryForList("SELECT name FROM payees WHERE is_system = TRUE").stream()
                            .anyMatch(
                                    row ->
                                            target.get("name")
                                                    .equalsIgnoreCase(
                                                            plain("payees", row).get("name"))))
                return true;
            for (Map.Entry<Key, Map<String, Object>> existing : current.rows().entrySet()) {
                if (!existing.getKey().table().equals(change.table())
                        || existing.getKey().id() == change.id()) continue;
                Map<String, String> candidate =
                        destinations.containsKey(existing.getKey())
                                ? destinations.get(existing.getKey())
                                : plain(change.table(), existing.getValue());
                if (candidate != null
                        && target.get("name").equalsIgnoreCase(candidate.get("name"))
                        && (change.table().equals("payees")
                                || Objects.equals(
                                        target.get("parent_id"), candidate.get("parent_id"))))
                    return true;
            }
        }
        return false;
    }

    private boolean valid(Change change, Long userId) {
        if (!TABLES.contains(change.table()) || change.id() <= 0) return false;
        for (Map<String, String> row : java.util.Arrays.asList(change.before(), change.after())) {
            if (row == null) continue;
            if (!Long.toString(change.id()).equals(row.get("id"))
                    || !columns(change.table()).keySet().equals(row.keySet())) return false;
            if (row.containsKey("user_id") && !userId.toString().equals(row.get("user_id")))
                return false;
        }
        return true;
    }

    public void lockRows(HistoryChangeSet changes) {
        if (changes.changes().stream().anyMatch(change -> !TABLES.contains(change.table())))
            throw new org.openfinance.exception.HistoryConflictException("legacy");
        if (sqlite) return; // The write gate already owns SQLite's writer lock.
        changes.changes().stream()
                .sorted(Comparator.comparing(Change::table).thenComparingLong(Change::id))
                .forEach(
                        change ->
                                jdbc.queryForList(
                                        "SELECT id FROM "
                                                + identifier(change.table())
                                                + " WHERE id = ? FOR UPDATE",
                                        change.id()));
    }

    public void restore(HistoryChangeSet changeSet, Snapshot current, boolean redo, Long userId) {
        String unavailable = unavailable(changeSet, current, redo, userId);
        if (unavailable != null)
            throw new org.openfinance.exception.HistoryConflictException(unavailable);
        entityManager.flush();
        // A dependency cannot be changed outside the owning user's graph, even in a malformed
        // database.
        assertNoForeignReferences(changeSet, userId);
        List<Change> ordered = new ArrayList<>(changeSet.changes());
        ordered.sort(
                Comparator.comparingInt((Change change) -> TABLES.indexOf(change.table()))
                        .thenComparingLong(Change::id));
        List<Change> reverse = new ArrayList<>(ordered);
        Collections.reverse(reverse);
        for (Change change : reverse) {
            if ((redo ? change.after() : change.before()) == null)
                jdbc.update(
                        "DELETE FROM "
                                + identifier(change.table())
                                + " WHERE id = ? AND "
                                + owned(change.table()),
                        change.id(),
                        userId);
        }
        for (Change change : ordered) {
            Map<String, String> target = redo ? change.after() : change.before();
            if (target == null) continue;
            if ((redo ? change.before() : change.after()) == null) insert(change.table(), target);
            else update(change, target, current.rows().get(change.key()), redo, userId);
        }
        entityManager.clear();
        jdbc.update("DELETE FROM net_worth WHERE user_id = ?", userId);
        encryption.refreshSearchTokens(
                userId,
                EncryptionContext.getKey(),
                changeSet.changes().stream()
                        .collect(
                                Collectors.groupingBy(
                                        Change::table,
                                        Collectors.mapping(Change::id, Collectors.toSet()))));
    }

    private void assertNoForeignReferences(HistoryChangeSet set, Long userId) {
        Map<String, List<Long>> affected =
                set.changes().stream()
                        .collect(
                                Collectors.groupingBy(
                                        Change::table,
                                        Collectors.mapping(Change::id, Collectors.toList())));
        affected.forEach(
                (parent, ids) -> {
                    for (int start = 0; start < ids.size(); start += 500) {
                        List<Long> batch = ids.subList(start, Math.min(ids.size(), start + 500));
                        String parameters =
                                String.join(",", Collections.nCopies(batch.size(), "?"));
                        List<Object> arguments = new ArrayList<>(batch);
                        arguments.add(userId);
                        for (Map.Entry<EntityType, String> entity :
                                HistoryDomainRegistry.ENTITIES.entrySet()) {
                            if (!entity.getValue().equals(parent)) continue;
                            List<Object> attachmentArguments = new ArrayList<>(arguments);
                            attachmentArguments.add(entity.getKey().name());
                            if (jdbc.queryForObject(
                                            "SELECT COUNT(*) FROM attachments WHERE entity_id IN ("
                                                    + parameters
                                                    + ") AND user_id <> ? AND entity_type = ?",
                                            Integer.class,
                                            attachmentArguments.toArray())
                                    > 0)
                                throw new org.openfinance.exception.HistoryConflictException(
                                        "dependencies");
                        }
                        for (String table : TABLES) {
                            for (Map.Entry<String, String> ref :
                                    HistoryDomainRegistry.REFERENCES.entrySet()) {
                                if (!ref.getValue().equals(parent)
                                        || !columns(table).containsKey(ref.getKey())) continue;
                                Integer count =
                                        jdbc.queryForObject(
                                                "SELECT COUNT(*) FROM "
                                                        + identifier(table)
                                                        + " WHERE "
                                                        + identifier(ref.getKey())
                                                        + " IN ("
                                                        + parameters
                                                        + ") AND NOT ("
                                                        + owned(table)
                                                        + ")",
                                                Integer.class,
                                                arguments.toArray());
                                if (count != null && count > 0)
                                    throw new org.openfinance.exception.HistoryConflictException(
                                            "dependencies");
                            }
                        }
                    }
                });
    }

    public String label(String table, Long id, Long userId) {
        if (!columns(table).containsKey("name")) return null;
        return jdbc
                .queryForList(
                        "SELECT name FROM "
                                + identifier(table)
                                + " WHERE id = ? AND "
                                + owned(table),
                        id,
                        userId)
                .stream()
                .findFirst()
                .map(row -> plain(table, row).get("name"))
                .orElse(null);
    }

    private void insert(String table, Map<String, String> target) {
        List<String> fields = new ArrayList<>(target.keySet());
        String sql =
                "INSERT INTO "
                        + identifier(table)
                        + " ("
                        + fields.stream()
                                .map(EncryptedUserDataService::identifier)
                                .collect(Collectors.joining(","))
                        + ")"
                        + (sqlite ? "" : " OVERRIDING SYSTEM VALUE")
                        + " VALUES ("
                        + String.join(",", Collections.nCopies(fields.size(), "?"))
                        + ")";
        jdbc.update(
                sql,
                fields.stream().map(field -> value(table, field, target.get(field))).toArray());
    }

    private void update(
            Change change,
            Map<String, String> target,
            Map<String, Object> raw,
            boolean redo,
            Long userId) {
        Map<String, String> values = new LinkedHashMap<>(target);
        Map<String, String> actual = plain(change.table(), raw);
        if (change.additiveBalance())
            values.put(
                    "balance",
                    new BigDecimal(actual.get("balance"))
                            .add(change.balanceDelta(redo))
                            .toPlainString());
        if (values.containsKey("version"))
            values.put("version", Long.toString(Long.parseLong(actual.get("version")) + 1));
        List<String> fields =
                values.keySet().stream()
                        .filter(field -> !field.equals("id") && !field.equals("user_id"))
                        .toList();
        List<Object> arguments = new ArrayList<>();
        fields.forEach(field -> arguments.add(value(change.table(), field, values.get(field))));
        arguments.add(change.id());
        arguments.add(userId);
        jdbc.update(
                "UPDATE "
                        + identifier(change.table())
                        + " SET "
                        + fields.stream()
                                .map(field -> identifier(field) + " = ?")
                                .collect(Collectors.joining(","))
                        + " WHERE id = ? AND "
                        + owned(change.table()),
                arguments.toArray());
    }

    private Object value(String table, String column, String text) {
        if (text == null) return null;
        if (table.equals("attachments") && column.equals("file_data"))
            return encryption.translate(
                    table,
                    column,
                    Base64.getDecoder().decode(text),
                    null,
                    EncryptionContext.getKey());
        if (encryption.isEncryptedColumn(table, column))
            return encryption.translate(table, column, text, null, EncryptionContext.getKey());
        int type = columns(table).get(column);
        // SQLite's DATETIME declaration does not describe its stored representation: JPA uses
        // ISO text, whereas binding java.sql.Timestamp would silently convert it to epoch millis.
        if (sqlite
                && type != Types.BINARY
                && type != Types.VARBINARY
                && type != Types.LONGVARBINARY
                && type != Types.BLOB) return text;
        return switch (type) {
            case Types.BIGINT, Types.INTEGER, Types.SMALLINT, Types.TINYINT -> Long.valueOf(text);
            case Types.NUMERIC,
                    Types.DECIMAL,
                    Types.REAL,
                    Types.FLOAT,
                    Types.DOUBLE -> new BigDecimal(text);
            case Types.BIT, Types.BOOLEAN -> text.equals("1") || Boolean.parseBoolean(text);
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> java.sql.Timestamp.valueOf(
                    text.replace('T', ' '));
            case Types.DATE -> java.sql.Date.valueOf(text);
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> Base64
                    .getDecoder()
                    .decode(text);
            default -> text;
        };
    }
}
