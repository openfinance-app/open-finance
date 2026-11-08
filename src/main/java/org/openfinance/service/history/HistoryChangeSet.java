package org.openfinance.service.history;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Versioned, encrypted-at-rest persistence payload; never a presentation DTO or executable SQL. */
public record HistoryChangeSet(int format, List<Change> changes, List<Guard> guards) {
    public static final int FORMAT = 1;
    public static final Set<String> TECHNICAL = Set.of("version", "updated_at");

    public record Key(String table, String id) {
        public Key(String table, long id) {
            this(table, Long.toString(id));
        }
    }

    public record Change(
            String table, String id, Map<String, String> before, Map<String, String> after) {
        public Change(
                String table, long id, Map<String, String> before, Map<String, String> after) {
            this(table, Long.toString(id), before, after);
        }

        public Key key() {
            return new Key(table, id);
        }

        // Booked account movements are additive. Other account fields must still match exactly.
        public boolean additiveBalance() {
            return table.equals("accounts")
                    && before != null
                    && after != null
                    && Objects.equals(before.get("currency"), after.get("currency"));
        }

        public boolean balanceOnly() {
            return additiveBalance() && equal(before, after, true);
        }

        public BigDecimal balanceDelta(boolean redo) {
            BigDecimal delta =
                    new BigDecimal(after.get("balance"))
                            .subtract(new BigDecimal(before.get("balance")));
            return redo ? delta : delta.negate();
        }
    }

    /**
     * Incoming references are checked as well as row contents, including polymorphic attachments.
     */
    public record Reference(String table, String column, String id) {
        public Reference(String table, String column, long id) {
            this(table, column, Long.toString(id));
        }
    }

    public record Guard(String table, String id, List<Reference> before, List<Reference> after) {
        public Guard(String table, long id, List<Reference> before, List<Reference> after) {
            this(table, Long.toString(id), before, after);
        }
    }

    public static boolean equal(
            Map<String, String> a, Map<String, String> b, boolean ignoreBalance) {
        if (a == null || b == null) return a == b;
        Set<String> keys = new java.util.HashSet<>(a.keySet());
        keys.addAll(b.keySet());
        return keys.stream()
                .allMatch(
                        key ->
                                // Older captured account rows predate the nullable marker.
                                (key.equals("deleted_date")
                                                && a.get(key) == null
                                                && b.get(key) == null)
                                        || (a.containsKey(key)
                                                && b.containsKey(key)
                                                && (TECHNICAL.contains(key)
                                                        || (ignoreBalance && key.equals("balance"))
                                                        || Objects.equals(
                                                                a.get(key), b.get(key)))));
    }
}
