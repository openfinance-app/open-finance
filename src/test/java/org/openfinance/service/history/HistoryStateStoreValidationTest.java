package org.openfinance.service.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.exception.HistoryConflictException;
import org.openfinance.service.EncryptedUserDataService;
import org.openfinance.service.history.HistoryChangeSet.Change;
import org.openfinance.service.history.HistoryChangeSet.Guard;
import org.openfinance.service.history.HistoryChangeSet.Key;
import org.openfinance.service.history.HistoryChangeSet.Reference;
import org.openfinance.service.history.HistoryStateStore.Snapshot;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

@ExtendWith(MockitoExtension.class)
@DisplayName("Undo and redo protect ownership, dependencies and the persisted value types")
class HistoryStateStoreValidationTest {
    @Mock private JdbcTemplate jdbc;
    @Mock private EncryptedUserDataService encryption;
    @Mock private EntityManager entityManager;
    private HistoryStateStore store;
    private final Map<String, Map<String, Integer>> schema = new LinkedHashMap<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        store = new HistoryStateStore(jdbc, encryption, entityManager);
        lenient()
                .when(encryption.translate(anyString(), anyString(), any(), any(), any()))
                .thenAnswer(call -> call.getArgument(2));
        lenient()
                .when(jdbc.query(anyString(), any(ResultSetExtractor.class)))
                .thenAnswer(
                        call -> {
                            String sql = call.getArgument(0);
                            String table = sql.split("\"")[1];
                            Map<String, Integer> columns = schema.getOrDefault(table, Map.of());
                            ResultSet result = mock(ResultSet.class);
                            ResultSetMetaData metadata = mock(ResultSetMetaData.class);
                            when(result.getMetaData()).thenReturn(metadata);
                            when(metadata.getColumnCount()).thenReturn(columns.size());
                            int index = 1;
                            for (Map.Entry<String, Integer> column : columns.entrySet()) {
                                when(metadata.getColumnName(index)).thenReturn(column.getKey());
                                when(metadata.getColumnType(index++)).thenReturn(column.getValue());
                            }
                            return ((ResultSetExtractor<?>) call.getArgument(1))
                                    .extractData(result);
                        });
    }

    private Map<String, String> row(String table, String id, String... fields) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("user_id", "7");
        for (int i = 0; i < fields.length; i += 2) row.put(fields[i], fields[i + 1]);
        Map<String, Integer> types = new LinkedHashMap<>();
        row.keySet()
                .forEach(
                        key ->
                                types.put(
                                        key,
                                        key.equals("id") || key.equals("user_id")
                                                ? Types.BIGINT
                                                : Types.VARCHAR));
        schema.put(table, types);
        return row;
    }

    private Snapshot snapshot(String table, Map<String, String>... rows) {
        Map<Key, Map<String, Object>> data = new LinkedHashMap<>();
        for (Map<String, String> row : rows)
            data.put(new Key(table, row.get("id")), new LinkedHashMap<>(row));
        return new Snapshot(data, Map.of());
    }

    private HistoryChangeSet changes(Change change) {
        return new HistoryChangeSet(HistoryChangeSet.FORMAT, List.of(change), List.of());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "bad-format",
                "empty",
                "unknown-table",
                "null-id",
                "zero-id",
                "negative-id",
                "text-id",
                "wrong-id",
                "missing-column",
                "extra-column",
                "foreign-user",
                "bad-uuid",
                "short-uuid"
            })
    void malformedHistoryCannotReachDatabaseWrites(String defect) {
        String table = defect.contains("uuid") ? "budget_alerts" : "accounts";
        String id = "1";
        Map<String, String> original = row(table, id, "name", "Original");
        Map<String, String> target = new LinkedHashMap<>(original);
        switch (defect) {
            case "unknown-table" -> table = "users";
            case "null-id" -> id = null;
            case "zero-id" -> id = "0";
            case "negative-id" -> id = "-1";
            case "text-id" -> id = "1 OR 1=1";
            case "wrong-id" -> target.put("id", "2");
            case "missing-column" -> target.remove("name");
            case "extra-column" -> target.put("unknown", "value");
            case "foreign-user" -> target.put("user_id", "8");
            case "bad-uuid" -> id = "not-a-uuid";
            case "short-uuid" -> id = "1-1-1-1-1";
            default -> {}
        }
        HistoryChangeSet set =
                new HistoryChangeSet(
                        defect.equals("bad-format") ? 9 : 1,
                        defect.equals("empty")
                                ? List.of()
                                : List.of(new Change(table, id, target, original)),
                        List.of());
        Snapshot current = snapshot(table, original);
        assertThat(store.unavailable(set, current, false, 7L)).isEqualTo("legacy");
        assertThatThrownBy(() -> store.restore(set, current, false, 7L))
                .isInstanceOf(HistoryConflictException.class);
        verify(jdbc, never()).update(anyString(), any(Object[].class));
        verify(entityManager, never()).flush();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void acknowledgingAnAlertDoesNotPreventReversingItsFinancialAction(boolean redo) {
        String id = "00000000-0000-0000-0000-000000000001";
        Map<String, String> original = row("budget_alerts", id, "is_read", "false", "amount", "10");
        Map<String, String> updated = new LinkedHashMap<>(original);
        updated.put("amount", "20");
        Map<String, String> current = new LinkedHashMap<>(redo ? original : updated);
        current.put("is_read", "true");
        assertThat(
                        store.unavailable(
                                changes(new Change("budget_alerts", id, original, updated)),
                                snapshot("budget_alerts", current),
                                redo,
                                7L))
                .isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"deleted", "modified", "new-row"})
    void changedRowsCannotBeOverwrittenByHistory(String state) {
        Map<String, String> original = row("accounts", "1", "name", "Original");
        Map<String, String> current = new LinkedHashMap<>(original);
        current.put("name", "Someone's later edit");
        HistoryChangeSet set =
                changes(
                        new Change(
                                "accounts",
                                "1",
                                original,
                                state.equals("new-row") ? null : original));
        assertThat(
                        store.unavailable(
                                set,
                                state.equals("deleted")
                                        ? snapshot("accounts")
                                        : snapshot("accounts", current),
                                false,
                                7L))
                .isEqualTo("changed");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aNewDependencyBlocksUndoAndRedo(boolean redo) {
        Map<String, String> account = row("accounts", "1", "name", "Account");
        Reference dependency = new Reference("transactions", "account_id", "10");
        HistoryChangeSet set =
                new HistoryChangeSet(
                        1,
                        List.of(new Change("accounts", "1", account, account)),
                        List.of(new Guard("accounts", "1", List.of(), List.of())));
        Snapshot current =
                new Snapshot(
                        snapshot("accounts", account).rows(),
                        Map.of(new Key("accounts", "1"), List.of(dependency)));
        assertThat(store.unavailable(set, current, redo, 7L)).isEqualTo("dependencies");
    }

    @ParameterizedTest
    @CsvSource({
        "payees,grocer,grocer,1,1,true",
        "payees,GROCER,grocer,1,2,true",
        "payees,grocer,baker,1,1,false",
        "categories,grocer,GROCER,1,1,true",
        "categories,grocer,grocer,1,2,false",
        "categories,grocer,baker,1,1,false"
    })
    void restoringNamesHonorsCaseInsensitivityAndCategoryParents(
            String table,
            String name,
            String otherName,
            String parent,
            String otherParent,
            boolean conflict) {
        Map<String, String> restored = row(table, "1", "name", name, "parent_id", parent);
        Map<String, String> existing = row(table, "2", "name", otherName, "parent_id", otherParent);
        assertThat(
                        store.unavailable(
                                changes(new Change(table, "1", restored, null)),
                                snapshot(table, existing),
                                false,
                                7L))
                .isEqualTo(conflict ? "dependencies" : null);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aBulkRenameCanReuseANameFreedByTheSameOperation(boolean deleteOther) {
        Map<String, String> before = row("payees", "1", "name", "Target");
        Map<String, String> after = row("payees", "1", "name", "Current");
        Map<String, String> otherAfter = row("payees", "2", "name", "Target");
        Map<String, String> otherBefore =
                deleteOther ? null : row("payees", "2", "name", "Available");
        HistoryChangeSet set =
                new HistoryChangeSet(
                        1,
                        List.of(
                                new Change("payees", "1", before, after),
                                new Change("payees", "2", otherBefore, otherAfter)),
                        List.of());
        assertThat(store.unavailable(set, snapshot("payees", after, otherAfter), false, 7L))
                .isNull();
    }

    @Test
    void systemPayeeNamesAreReservedDuringRestoration() {
        Map<String, String> payee = row("payees", "1", "name", "Balance Adjustment");
        when(jdbc.queryForList("SELECT name FROM payees WHERE is_system = TRUE"))
                .thenReturn(List.of(Map.of("name", "BALANCE ADJUSTMENT")));
        assertThat(
                        store.unavailable(
                                changes(new Change("payees", "1", payee, null)),
                                snapshot("payees"),
                                false,
                                7L))
                .isEqualTo("dependencies");
    }

    @ParameterizedTest
    @CsvSource({
        "1,MONTHLY,2024-12-01,2024-12-31,false",
        "1,MONTHLY,2025-02-01,2025-02-28,false",
        "1,MONTHLY,2024-12-01,2025-01-01,true",
        "1,MONTHLY,2025-01-31,2025-02-28,true",
        "1,MONTHLY,2025-01-10,2025-01-20,true",
        "2,MONTHLY,2025-01-01,2025-01-31,false",
        "1,YEARLY,2025-01-01,2025-01-31,false"
    })
    void restoringABudgetChecksInclusiveOverlapInItsOwnCategoryAndPeriod(
            String category, String period, String start, String end, boolean overlap) {
        Map<String, String> budget =
                row(
                        "budgets",
                        "1",
                        "category_id",
                        "1",
                        "period",
                        "MONTHLY",
                        "start_date",
                        "2025-01-01",
                        "end_date",
                        "2025-01-31");
        Map<String, String> existing =
                row(
                        "budgets",
                        "2",
                        "category_id",
                        category,
                        "period",
                        period,
                        "start_date",
                        start,
                        "end_date",
                        end);
        assertThat(
                        store.unavailable(
                                changes(new Change("budgets", "1", budget, null)),
                                snapshot("budgets", existing),
                                false,
                                7L))
                .isEqualTo(overlap ? "budgetOverlap" : null);
    }

    @Test
    void restoringThePreviousBudgetCanRemoveItsOverlappingReplacement() {
        Map<String, String> previous =
                row(
                        "budgets",
                        "1",
                        "category_id",
                        "1",
                        "period",
                        "MONTHLY",
                        "start_date",
                        "2025-01-01",
                        "end_date",
                        "2025-01-31");
        Map<String, String> replacement = new LinkedHashMap<>(previous);
        replacement.put("id", "2");
        HistoryChangeSet set =
                new HistoryChangeSet(
                        1,
                        List.of(
                                new Change("budgets", "1", previous, null),
                                new Change("budgets", "2", null, replacement)),
                        List.of());
        assertThat(store.unavailable(set, snapshot("budgets", replacement), false, 7L)).isNull();
    }

    static Stream<Arguments> databaseValues() {
        return Stream.of(
                Arguments.of(Types.BIGINT, "9007199254740993", 9007199254740993L),
                Arguments.of(Types.INTEGER, "42", 42L),
                Arguments.of(Types.SMALLINT, "-3", -3L),
                Arguments.of(Types.TINYINT, "4", 4L),
                Arguments.of(
                        Types.NUMERIC,
                        "9007199254740992.01",
                        new BigDecimal("9007199254740992.01")),
                Arguments.of(
                        Types.DECIMAL,
                        "0.000000000000000001",
                        new BigDecimal("0.000000000000000001")),
                Arguments.of(Types.REAL, "1.25", new BigDecimal("1.25")),
                Arguments.of(Types.FLOAT, "1.25", new BigDecimal("1.25")),
                Arguments.of(Types.DOUBLE, "1.25", new BigDecimal("1.25")),
                Arguments.of(Types.BOOLEAN, "true", true),
                Arguments.of(Types.BOOLEAN, "false", false),
                Arguments.of(Types.BIT, "1", true),
                Arguments.of(Types.BIT, "0", false),
                Arguments.of(
                        Types.TIMESTAMP,
                        "2025-01-02T12:34:56",
                        java.sql.Timestamp.valueOf("2025-01-02 12:34:56")),
                Arguments.of(
                        Types.TIMESTAMP_WITH_TIMEZONE,
                        "2025-01-02 12:34:56",
                        java.sql.Timestamp.valueOf("2025-01-02 12:34:56")),
                Arguments.of(Types.DATE, "2025-01-02", java.sql.Date.valueOf("2025-01-02")),
                Arguments.of(Types.BINARY, "AQID", new byte[] {1, 2, 3}),
                Arguments.of(Types.VARBINARY, "AQID", new byte[] {1, 2, 3}),
                Arguments.of(Types.LONGVARBINARY, "AQID", new byte[] {1, 2, 3}),
                Arguments.of(Types.BLOB, "AQID", new byte[] {1, 2, 3}),
                Arguments.of(Types.VARCHAR, "plain text", "plain text"),
                Arguments.of(Types.VARCHAR, null, null));
    }

    @ParameterizedTest
    @MethodSource("databaseValues")
    void postgresRestorationBindsNativeColumnTypesWithoutLosingPrecision(
            int type, String value, Object expected) {
        Map<String, String> target = row("institutions", "1", "value", value);
        schema.get("institutions").put("value", type);
        store.restore(
                changes(new Change("institutions", "1", target, null)),
                snapshot("institutions"),
                false,
                7L);
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc)
                .update(
                        eq(
                                "INSERT INTO \"institutions\" (\"id\",\"user_id\",\"value\") OVERRIDING SYSTEM VALUE VALUES (?,?,?)"),
                        arguments.capture());
        assertThat(arguments.getValue()[0]).isEqualTo(1L);
        assertThat(arguments.getValue()[1]).isEqualTo(7L);
        assertThat(arguments.getValue()[2]).isEqualTo(expected);
        verify(encryption).refreshSearchTokens(7L, null, Map.of("institutions", Set.of(1L)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"created", "deleted", "changed", "same", "technical", "new-column"})
    void differencesCaptureBusinessChangesAndIgnoreTechnicalUpdates(String operation) {
        Map<String, String> before =
                row("institutions", "1", "name", "Bank", "updated_at", "2025-01-01");
        Map<String, String> after = new LinkedHashMap<>(before);
        if (operation.equals("changed")) after.put("name", "New bank");
        if (operation.equals("technical")) after.put("updated_at", "2025-01-02");
        if (operation.equals("new-column")) after.put("code", "B");
        HistoryChangeSet diff =
                store.difference(
                        operation.equals("created")
                                ? snapshot("institutions")
                                : snapshot("institutions", before),
                        operation.equals("deleted")
                                ? snapshot("institutions")
                                : snapshot("institutions", after));
        assertThat(diff.changes()).hasSize(Set.of("same", "technical").contains(operation) ? 0 : 1);
        if (!diff.changes().isEmpty()) {
            assertThat(diff.changes().getFirst().before())
                    .isEqualTo(operation.equals("created") ? null : before);
            assertThat(diff.changes().getFirst().after())
                    .isEqualTo(operation.equals("deleted") ? null : after);
            assertThat(diff.guards()).hasSize(1);
        }
    }

    @Test
    void changedAttachmentsRequireTheirCompleteBinaryContent() {
        Map<String, String> attachment = row("attachments", "1", "name", "Statement");
        assertThatThrownBy(
                        () ->
                                store.difference(
                                        snapshot("attachments", attachment),
                                        snapshot("attachments")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("complete history capture");
    }

    @Test
    void arbitraryTablesAreRejectedBeforeMetadataQueriesOrLocks() {
        assertThatThrownBy(() -> store.columns("users"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.lockRows(changes(new Change("users", "1", null, Map.of()))))
                .isInstanceOf(HistoryConflictException.class);
        verify(jdbc, never()).queryForList(anyString(), any(Object[].class));
    }
}
