package org.openfinance.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openfinance.entity.OperationType;

@DisplayName("History record details")
class HistoryRecordChangeTest {
    @Test
    @DisplayName("Snapshots ordered context and field lists without losing null or exact values")
    void snapshotsCollections() {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("name", "Savings");
        context.put("description", null);
        HistoryRecordChange.FieldChange field =
                new HistoryRecordChange.FieldChange("balance", null, "123.45678901");
        List<HistoryRecordChange.FieldChange> fields = new ArrayList<>(List.of(field));
        HistoryRecordChange change =
                new HistoryRecordChange(
                        "accounts", "1", "Savings", OperationType.CREATE, context, fields);

        context.put("name", "Changed after capture");
        fields.clear();

        assertThat(change.context().keySet()).containsExactly("name", "description");
        assertThat(change.context())
                .containsEntry("name", "Savings")
                .containsEntry("description", null);
        assertThat(change.fields()).containsExactly(field);
        assertThatThrownBy(() -> change.context().put("name", "Changed through accessor"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> change.fields().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
