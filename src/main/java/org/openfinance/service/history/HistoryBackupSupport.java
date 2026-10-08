package org.openfinance.service.history;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import org.openfinance.exception.BackupException;
import org.openfinance.service.EncryptedUserDataService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Reserves identifiers for deleted historical rows as well as the backup's live rows. */
@Component
@RequiredArgsConstructor
public class HistoryBackupSupport {
    private final HistoryPayloadCodec payloads;
    private final EncryptedUserDataService encryption;
    private final JdbcTemplate jdbc;

    public void reserve(
            List<Map<String, Object>> history, Map<String, Map<Long, Long>> ids, SecretKey key) {
        for (Map<String, Object> entry : history) {
            Object json = entry.get("action_state_json");
            if (json == null) continue;
            HistoryChangeSet state = payloads.read(encryption.plaintext(json.toString(), key));
            if (state.format() != HistoryChangeSet.FORMAT)
                throw BackupException.validation("Unsupported reversible history format");
            for (HistoryChangeSet.Change change : state.changes()) {
                reserve(ids, change.table(), change.id());
                for (Map<String, String> row :
                        java.util.Arrays.asList(change.before(), change.after())) {
                    if (row == null) continue;
                    row.forEach(
                            (column, value) -> {
                                String table = HistoryDomainRegistry.REFERENCES.get(column);
                                if (value != null
                                        && table != null
                                        && HistoryDomainRegistry.TABLES.contains(table))
                                    reserve(ids, table, Long.parseLong(value));
                            });
                }
            }
            for (HistoryChangeSet.Guard guard : state.guards()) {
                reserve(ids, guard.table(), guard.id());
                java.util.stream.Stream.concat(guard.before().stream(), guard.after().stream())
                        .forEach(ref -> reserve(ids, ref.table(), ref.id()));
            }
        }
    }

    private void reserve(Map<String, Map<Long, Long>> ids, String table, String id) {
        if (table.equals("budget_alerts")) {
            UUID.fromString(id);
            return;
        }
        reserve(ids, table, Long.parseLong(id));
    }

    private void reserve(Map<String, Map<Long, Long>> ids, String table, long id) {
        if (!HistoryDomainRegistry.TABLES.contains(table) || id <= 0)
            throw BackupException.validation("Invalid reversible history reference");
        Map<Long, Long> tableIds = ids.get(table);
        if (tableIds.containsKey(id)) return;
        Long maximum =
                jdbc.queryForObject(
                        "SELECT COALESCE(MAX(id), 0) FROM "
                                + EncryptedUserDataService.identifier(table),
                        Long.class);
        if (table.equals("transactions") || table.equals("transactions_archive")) {
            Long transactionMaximum =
                    jdbc.queryForObject(
                            "SELECT MAX(id) FROM (SELECT COALESCE(MAX(id), 0) AS id FROM transactions UNION ALL SELECT COALESCE(MAX(id), 0) AS id FROM transactions_archive) maxima",
                            Long.class);
            maximum =
                    Math.max(
                            maximum == null ? 0 : maximum,
                            transactionMaximum == null ? 0 : transactionMaximum);
        }
        long next =
                Math.max(
                                maximum == null ? 0 : maximum,
                                tableIds.values().stream()
                                        .mapToLong(Long::longValue)
                                        .max()
                                        .orElse(0))
                        + 1;
        tableIds.put(id, next);
    }

    public String remap(
            String json,
            Map<String, Map<Long, Long>> ids,
            Long userId,
            Map<String, String> transfers) {
        HistoryChangeSet state = payloads.read(json);
        List<HistoryChangeSet.Change> changes = new ArrayList<>();
        for (HistoryChangeSet.Change change : state.changes())
            changes.add(
                    new HistoryChangeSet.Change(
                            change.table(),
                            mapped(ids, change.table(), change.id(), transfers),
                            row(change.table(), change.before(), ids, userId, transfers),
                            row(change.table(), change.after(), ids, userId, transfers)));
        List<HistoryChangeSet.Guard> guards =
                state.guards().stream()
                        .map(
                                guard ->
                                        new HistoryChangeSet.Guard(
                                                guard.table(),
                                                mapped(ids, guard.table(), guard.id(), transfers),
                                                references(guard.before(), ids, transfers),
                                                references(guard.after(), ids, transfers)))
                        .toList();
        return payloads.write(new HistoryChangeSet(state.format(), changes, guards));
    }

    private Map<String, String> row(
            String table,
            Map<String, String> source,
            Map<String, Map<Long, Long>> ids,
            Long userId,
            Map<String, String> transfers) {
        if (source == null) return null;
        Map<String, String> result = new LinkedHashMap<>(source);
        source.forEach(
                (column, value) -> {
                    if (value == null) return;
                    if (table.equals("import_sessions")
                            && column.equals("status")
                            && List.of("PENDING", "PARSING", "PARSED", "REVIEWING", "IMPORTING")
                                    .contains(value)) result.put(column, "FAILED");
                    else if (column.equals("user_id")) result.put(column, userId.toString());
                    else if (column.equals("transfer_id"))
                        result.put(
                                column,
                                transfers.computeIfAbsent(
                                        value, ignored -> UUID.randomUUID().toString()));
                    else {
                        String target =
                                column.equals("id")
                                        ? table
                                        : HistoryDomainRegistry.REFERENCES.get(column);
                        if (column.equals("entity_id"))
                            target =
                                    HistoryDomainRegistry.ENTITIES.entrySet().stream()
                                            .filter(
                                                    entry ->
                                                            entry.getKey()
                                                                    .name()
                                                                    .equals(
                                                                            source.get(
                                                                                    "entity_type")))
                                            .map(Map.Entry::getValue)
                                            .findFirst()
                                            .orElse(null);
                        if (target != null)
                            result.put(column, mapped(ids, target, value, transfers));
                    }
                });
        return result;
    }

    private List<HistoryChangeSet.Reference> references(
            List<HistoryChangeSet.Reference> source,
            Map<String, Map<Long, Long>> ids,
            Map<String, String> transfers) {
        return source.stream()
                .map(
                        reference ->
                                new HistoryChangeSet.Reference(
                                        reference.table(),
                                        reference.column(),
                                        mapped(ids, reference.table(), reference.id(), transfers)))
                .toList();
    }

    public static String remapAlertId(String id, Map<String, String> mappings) {
        UUID.fromString(id);
        return mappings.computeIfAbsent(
                "budget_alerts:" + id, ignored -> UUID.randomUUID().toString());
    }

    private static String mapped(
            Map<String, Map<Long, Long>> ids,
            String table,
            String id,
            Map<String, String> transfers) {
        return table.equals("budget_alerts")
                ? remapAlertId(id, transfers)
                : Long.toString(mapped(ids, table, Long.parseLong(id)));
    }

    private static long mapped(Map<String, Map<Long, Long>> ids, String table, long id) {
        Long result = ids.getOrDefault(table, Map.of()).get(id);
        if (result == null)
            throw BackupException.validation("Unresolved reversible history reference: " + table);
        return result;
    }

    /** Keep SQLite from assigning an ID reserved by an undone action to a new unrelated row. */
    public void reserveSequences(Map<String, Map<Long, Long>> ids) {
        for (String table : HistoryDomainRegistry.TABLES) {
            if (table.equals("budget_alerts")) continue;
            long maximum =
                    ids.getOrDefault(table, Map.of()).values().stream()
                            .mapToLong(Long::longValue)
                            .max()
                            .orElse(0);
            if (jdbc.update(
                            "UPDATE sqlite_sequence SET seq = MAX(seq, ?) WHERE name = ?",
                            maximum,
                            table)
                    == 0)
                jdbc.update(
                        "INSERT INTO sqlite_sequence (name, seq) VALUES (?, ?)", table, maximum);
        }
    }
}
