package org.openfinance.service.history;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HistoryChangeSetTest {
    @Test
    void legacyRowsTolerateAnAbsentNullableDeletionMarkerButNotADeletion() {
        Map<String, String> legacy = Map.of("id", "1", "balance", "100");
        Map<String, String> current = new HashMap<>(legacy);
        current.put("deleted_date", null);
        assertThat(HistoryChangeSet.equal(legacy, current, false)).isTrue();
        current.put("deleted_date", "2026-01-01");
        assertThat(HistoryChangeSet.equal(legacy, current, false)).isFalse();
        assertThat(HistoryChangeSet.equal(legacy, current, true)).isFalse();
        current.remove("deleted_date");
        current.put("other", null);
        assertThat(HistoryChangeSet.equal(legacy, current, false)).isFalse();
    }
}
