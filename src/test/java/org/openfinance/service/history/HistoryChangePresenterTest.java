package org.openfinance.service.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
}
