package org.openfinance.service.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.HistoryRecordChange;
import org.openfinance.entity.EntityType;
import org.openfinance.entity.OperationHistory;
import org.openfinance.entity.OperationType;

@ExtendWith(MockitoExtension.class)
class HistoryChangePresenterTest {
    @Mock private HistoryStateStore states;
    private HistoryChangePresenter presenter;

    @BeforeEach
    void setUp() {
        presenter = new HistoryChangePresenter(states, new ObjectMapper());
    }

    private OperationHistory entry() {
        return OperationHistory.builder()
                .userId(7L)
                .entityId(12L)
                .entityType(EntityType.ACCOUNT)
                .entityLabel("Savings")
                .operationType(OperationType.UPDATE)
                .build();
    }

    @Test
    @DisplayName("Child interest changes retain exact old/new values and readable account context")
    void presentsChildChanges() {
        when(states.label("accounts", 12L, 7L)).thenReturn("Savings");
        Map<String, String> before =
                Map.of(
                        "account_id",
                        "12",
                        "rate",
                        "0.50",
                        "tax_rate",
                        "30",
                        "valid_from",
                        "2026-01-01",
                        "user_id",
                        "7",
                        "version",
                        "2");
        Map<String, String> after = new HashMap<>(before);
        after.put("rate", "0.75");
        after.put("tax_rate", "31.4");
        after.put("version", "3");
        List<HistoryRecordChange> records =
                presenter.present(
                        entry(),
                        new HistoryChangeSet(
                                1,
                                List.of(
                                        new HistoryChangeSet.Change(
                                                "interest_rate_variations", 23L, before, after)),
                                List.of()));
        assertThat(records).hasSize(1);
        assertThat(records.getFirst().context()).containsEntry("account_id", "Savings");
        assertThat(records.getFirst().fields())
                .containsExactly(
                        new HistoryRecordChange.FieldChange("rate", "0.50", "0.75"),
                        new HistoryRecordChange.FieldChange("tax_rate", "30", "31.4"));
    }

    @Test
    @DisplayName("Deletion shows captured entity values, and creation retains explicit nulls")
    void presentsCreationDeletionAndNulls() throws Exception {
        Map<String, String> category = Map.of("name", "Food", "user_id", "7");
        Map<String, String> budget =
                Map.of("category_id", "5", "amount", "100.12345678", "currency", "EUR");
        HistoryChangeSet state =
                new HistoryChangeSet(
                        1,
                        List.of(
                                new HistoryChangeSet.Change("categories", 5L, category, null),
                                new HistoryChangeSet.Change("budgets", 6L, null, budget)),
                        List.of());
        List<HistoryRecordChange> records = presenter.present(entry(), state);
        assertThat(records.getFirst().entityLabel()).isEqualTo("Food");
        assertThat(records.getFirst().operationType()).isEqualTo(OperationType.DELETE);
        assertThat(records.getFirst().fields())
                .contains(new HistoryRecordChange.FieldChange("name", "Food", null));
        assertThat(records.get(1).context()).containsEntry("category_id", "Food");
        String json =
                new ObjectMapper()
                        .setSerializationInclusion(
                                com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
                        .writeValueAsString(records.get(1));
        assertThat(json).contains("\"before\":null", "100.12345678");
        assertThat(json).doesNotContain("user_id");
    }

    @Test
    @DisplayName("Related import records reuse account labels and retain changed fees exactly")
    void reusesReferenceLabels() {
        when(states.label("accounts", 12L, 7L)).thenReturn("Savings");
        HistoryChangeSet state =
                new HistoryChangeSet(
                        1,
                        List.of(
                                new HistoryChangeSet.Change(
                                        "transactions",
                                        31L,
                                        Map.of("account_id", "12", "sale_fees", "1.2500"),
                                        Map.of("account_id", "12", "sale_fees", "2.5000")),
                                new HistoryChangeSet.Change(
                                        "transactions",
                                        32L,
                                        null,
                                        Map.of("account_id", "12", "amount", "20.12345678"))),
                        List.of());

        List<HistoryRecordChange> records = presenter.present(entry(), state);

        assertThat(records).hasSize(2);
        assertThat(records.getFirst().fields())
                .containsExactly(
                        new HistoryRecordChange.FieldChange("sale_fees", "1.2500", "2.5000"));
        assertThat(records.get(1).context()).containsEntry("account_id", "Savings");
        verify(states).label("accounts", 12L, 7L);
    }

    @Test
    @DisplayName("Older diffs with an omitted null side still produce readable changes")
    void presentsOlderDiff() {
        OperationHistory entry = entry();
        entry.setChangedFieldsJson("{\"notes\":{\"after\":\"Updated instructions\"}}");
        assertThat(presenter.present(entry, null).getFirst().fields())
                .containsExactly(
                        new HistoryRecordChange.FieldChange("notes", null, "Updated instructions"));
    }

    @Test
    @DisplayName("Technical-only writes do not appear as user-visible history changes")
    void omitsTechnicalOnlyChanges() {
        HistoryChangeSet state =
                new HistoryChangeSet(
                        1,
                        List.of(
                                new HistoryChangeSet.Change(
                                        "accounts",
                                        12L,
                                        Map.of("name", "Savings", "version", "1"),
                                        Map.of("name", "Savings", "version", "2"))),
                        List.of());
        assertThat(presenter.present(entry(), state)).isEmpty();
        verifyNoInteractions(states);
    }

    @ParameterizedTest
    @CsvSource({"accounts,12,Savings", "accounts,13,", "transactions,12,"})
    @DisplayName("Only the primary entity may inherit the history entry's label")
    void usesFallbackLabelOnlyForMatchingPrimaryEntity(String table, long id, String expected) {
        HistoryChangeSet state =
                new HistoryChangeSet(
                        1,
                        List.of(
                                new HistoryChangeSet.Change(
                                        table, id, Map.of("amount", "1"), Map.of("amount", "2"))),
                        List.of());
        assertThat(presenter.present(entry(), state).getFirst().entityLabel()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"1,true", "true,true", "TRUE,true", "0,false", "false,false"})
    @DisplayName("History flags from SQLite and textual snapshots are presented as booleans")
    void normalizesFlagRepresentations(String raw, boolean expected) {
        HistoryChangeSet state =
                new HistoryChangeSet(
                        1,
                        List.of(
                                new HistoryChangeSet.Change(
                                        "accounts", 12L, null, Map.of("is_active", raw))),
                        List.of());
        assertThat(presenter.present(entry(), state).getFirst().fields())
                .containsExactly(new HistoryRecordChange.FieldChange("is_active", null, expected));
    }

    @Test
    @DisplayName("A renamed captured reference preserves its distinct before and after labels")
    void resolvesBothSidesOfCapturedRenames() {
        HistoryChangeSet state =
                new HistoryChangeSet(
                        1,
                        List.of(
                                new HistoryChangeSet.Change(
                                        "categories",
                                        5L,
                                        Map.of("name", "Old"),
                                        Map.of("name", "New")),
                                new HistoryChangeSet.Change(
                                        "transactions", 31L, Map.of("category_id", "5"), null),
                                new HistoryChangeSet.Change(
                                        "transactions", 32L, null, Map.of("category_id", "5"))),
                        List.of());
        List<HistoryRecordChange> result = presenter.present(entry(), state);
        assertThat(result.get(1).fields())
                .containsExactly(new HistoryRecordChange.FieldChange("category_id", "Old", null));
        assertThat(result.get(2).fields())
                .containsExactly(new HistoryRecordChange.FieldChange("category_id", null, "New"));
        verifyNoInteractions(states);
    }

    @Test
    @DisplayName("A newly captured reference remains readable in a before-side deletion")
    void usesOtherCapturedSideWhenReferenceDidNotPreviouslyExist() {
        HistoryChangeSet state =
                new HistoryChangeSet(
                        1,
                        List.of(
                                new HistoryChangeSet.Change(
                                        "categories", 5L, null, Map.of("name", "New")),
                                new HistoryChangeSet.Change(
                                        "transactions", 31L, Map.of("category_id", "5"), null)),
                        List.of());
        assertThat(presenter.present(entry(), state).get(1).context())
                .containsEntry("category_id", "New");
        verifyNoInteractions(states);
    }

    @Test
    @DisplayName("Nameless captured references use an owner-scoped lookup and cache missing labels")
    void retainsReferenceIdentityWhenLookupIsMissing() {
        HistoryChangeSet state =
                new HistoryChangeSet(
                        1,
                        List.of(
                                new HistoryChangeSet.Change(
                                        "categories", 5L, Map.of("name", " "), Map.of("name", "")),
                                new HistoryChangeSet.Change(
                                        "transactions", 31L, null, Map.of("category_id", "5")),
                                new HistoryChangeSet.Change(
                                        "transactions", 32L, null, Map.of("category_id", "5"))),
                        List.of());
        List<HistoryRecordChange> result = presenter.present(entry(), state);
        assertThat(result.get(1).context()).containsEntry("category_id", "#5");
        assertThat(result.get(2).context()).containsEntry("category_id", "#5");
        verify(states).label("categories", 5L, 7L);
    }

    @Test
    @DisplayName("Legacy textual reference values are preserved without a numeric lookup")
    void retainsNonNumericReferenceText() {
        HistoryChangeSet state =
                new HistoryChangeSet(
                        1,
                        List.of(
                                new HistoryChangeSet.Change(
                                        "transactions",
                                        31L,
                                        null,
                                        Map.of("category_id", "Uncategorized"))),
                        List.of());
        assertThat(presenter.present(entry(), state).getFirst().context())
                .containsEntry("category_id", "Uncategorized");
        verifyNoInteractions(states);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"{bad", "[]", "null", "{}", "{\"name\":\"legacy\",\"other\":{}}", " "})
    @DisplayName(
            "Unreadable legacy diffs retain identity without inventing changes or failing history")
    void toleratesLegacyDiffWithoutReadableFields(String diff) {
        OperationHistory history = entry();
        history.setChangedFieldsJson(diff);
        HistoryRecordChange result = presenter.present(history, null).getFirst();
        assertThat(result.entityLabel()).isEqualTo("Savings");
        assertThat(result.fields()).isEmpty();
    }

    @Test
    @DisplayName("A legacy removal preserves the old value even when no after member exists")
    void presentsLegacyRemoval() {
        OperationHistory history = entry();
        history.setChangedFieldsJson("{\"notes\":{\"before\":\"Remove me\"}}");
        assertThat(presenter.present(history, null).getFirst().fields())
                .containsExactly(new HistoryRecordChange.FieldChange("notes", "Remove me", null));
    }
}
