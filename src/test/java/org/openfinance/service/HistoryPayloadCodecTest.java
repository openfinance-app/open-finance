package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.openfinance.service.history.HistoryChangeSet;
import org.openfinance.service.history.HistoryPayloadCodec;

class HistoryPayloadCodecTest {
    private final HistoryPayloadCodec codec = new HistoryPayloadCodec();

    @Test
    void readsNumericIdsFromExistingFormatOneHistory() {
        HistoryChangeSet state =
                codec.read(
                        """
                {"format":1,"changes":[{"table":"budgets","id":12,"before":null,
                "after":{"id":"12","amount":"100"}}],"guards":[{"table":"categories",
                "id":3,"before":[],"after":[{"table":"budgets","column":"category_id","id":12}]}]}
                """);
        assertThat(state.changes().getFirst().id()).isEqualTo("12");
        assertThat(state.guards().getFirst().after().getFirst().id()).isEqualTo("12");
        assertThat(codec.read(codec.write(state))).isEqualTo(state);
    }

    @Test
    void preservesUuidChildIdentifiersWithoutNumericCoercion() {
        String id = "53b1df2c-a12c-43cf-84b4-723a2d0b0147";
        HistoryChangeSet state =
                new HistoryChangeSet(
                        1,
                        List.of(
                                new HistoryChangeSet.Change(
                                        "budget_alerts",
                                        id,
                                        null,
                                        Map.of("id", id, "budget_id", "12"))),
                        List.of());
        assertThat(codec.read(codec.write(state))).isEqualTo(state);
    }
}
