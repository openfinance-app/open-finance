package org.openfinance.service.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.service.EncryptedUserDataService;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class HistoryBackupSupportTest {
    @Mock private EncryptedUserDataService encryption;
    @Mock private JdbcTemplate jdbc;

    @Test
    void legacyHistoryWithoutReversibleStateNeedsNoNewIdentifiers() {
        new HistoryBackupSupport(new HistoryPayloadCodec(), encryption, jdbc)
                .reserve(List.of(Map.of("entity_id", 7L)), Map.of(), null);
        verifyNoInteractions(encryption, jdbc);
    }

    @ParameterizedTest
    @ValueSource(strings = {"unsupported-format", "unknown-table", "zero-id", "negative-id"})
    void rejectsUntrustedReversibleHistoryBeforeReservingIdentifiers(String invalid) {
        HistoryPayloadCodec codec = new HistoryPayloadCodec();
        String payload =
                codec.write(
                        new HistoryChangeSet(
                                invalid.equals("unsupported-format") ? 99 : HistoryChangeSet.FORMAT,
                                List.of(
                                        new HistoryChangeSet.Change(
                                                invalid.equals("unknown-table")
                                                        ? "users"
                                                        : "accounts",
                                                invalid.equals("zero-id")
                                                        ? "0"
                                                        : invalid.equals("negative-id")
                                                                ? "-1"
                                                                : "7",
                                                Map.of("id", "7"),
                                                null)),
                                List.of()));
        when(encryption.plaintext(payload, null)).thenReturn(payload);
        assertThatThrownBy(
                        () ->
                                new HistoryBackupSupport(codec, encryption, jdbc)
                                        .reserve(
                                                List.of(Map.of("action_state_json", payload)),
                                                Map.of("accounts", new LinkedHashMap<>()),
                                                null))
                .isInstanceOf(org.openfinance.exception.BackupException.class);
        verifyNoInteractions(jdbc);
    }

    @Test
    void nullDatabaseMaximumStillReservesHistoricalAccountIds() {
        HistoryPayloadCodec codec = new HistoryPayloadCodec();
        String payload =
                codec.write(
                        new HistoryChangeSet(
                                HistoryChangeSet.FORMAT,
                                List.of(
                                        new HistoryChangeSet.Change(
                                                "accounts", "7", Map.of("id", "7"), null)),
                                List.of()));
        when(encryption.plaintext(payload, null)).thenReturn(payload);
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn((Long) null);
        Map<Long, Long> ids = new LinkedHashMap<>();
        new HistoryBackupSupport(codec, encryption, jdbc)
                .reserve(
                        List.of(Map.of("action_state_json", payload)),
                        Map.of("accounts", ids),
                        null);
        assertThat(ids).containsEntry(7L, 1L);
    }

    @Test
    void remappingKeepsTransferPairsTogetherAndUpdatesAttachmentEntityReferences() {
        HistoryPayloadCodec codec = new HistoryPayloadCodec();
        String payload =
                codec.write(
                        new HistoryChangeSet(
                                HistoryChangeSet.FORMAT,
                                List.of(
                                        new HistoryChangeSet.Change(
                                                "transactions",
                                                "1",
                                                null,
                                                Map.of(
                                                        "id",
                                                        "1",
                                                        "transfer_id",
                                                        "old-transfer",
                                                        "user_id",
                                                        "4")),
                                        new HistoryChangeSet.Change(
                                                "transactions",
                                                "2",
                                                null,
                                                Map.of(
                                                        "id",
                                                        "2",
                                                        "transfer_id",
                                                        "old-transfer",
                                                        "user_id",
                                                        "4")),
                                        new HistoryChangeSet.Change(
                                                "attachments",
                                                "3",
                                                null,
                                                Map.of(
                                                        "id",
                                                        "3",
                                                        "entity_type",
                                                        "ACCOUNT",
                                                        "entity_id",
                                                        "7"))),
                                List.of()));
        Map<String, Map<Long, Long>> ids =
                Map.of(
                        "transactions",
                        Map.of(1L, 101L, 2L, 102L),
                        "attachments",
                        Map.of(3L, 103L),
                        "accounts",
                        Map.of(7L, 107L));
        HistoryChangeSet mapped =
                codec.read(
                        new HistoryBackupSupport(codec, encryption, jdbc)
                                .remap(payload, ids, 9L, new LinkedHashMap<>()));
        assertThat(mapped.changes().get(0).after().get("transfer_id"))
                .isNotEqualTo("old-transfer")
                .isEqualTo(mapped.changes().get(1).after().get("transfer_id"));
        assertThat(mapped.changes().get(0).after())
                .containsEntry("id", "101")
                .containsEntry("user_id", "9");
        assertThat(mapped.changes().get(2).after()).containsEntry("entity_id", "107");
    }

    @Test
    void remappingAnUnresolvedIdFailsInsteadOfPointingToAnotherUsersLiveRecord() {
        HistoryPayloadCodec codec = new HistoryPayloadCodec();
        String payload =
                codec.write(
                        new HistoryChangeSet(
                                HistoryChangeSet.FORMAT,
                                List.of(
                                        new HistoryChangeSet.Change(
                                                "accounts", "7", Map.of("id", "7"), null)),
                                List.of()));
        assertThatThrownBy(
                        () ->
                                new HistoryBackupSupport(codec, encryption, jdbc)
                                        .remap(payload, Map.of(), 9L, new LinkedHashMap<>()))
                .isInstanceOf(org.openfinance.exception.BackupException.class)
                .hasMessageContaining("Unresolved");
    }

    @ParameterizedTest
    @CsvSource(
            value = {"NULL,120,121", "120,NULL,121", "NULL,NULL,1", "120,500,501"},
            nullValues = "NULL")
    @DisplayName(
            "Restored transaction identifiers account for live and archived maxima, including nulls")
    void reservesDeletedTransactionIdentifier(
            Long liveMaximum, Long archiveMaximum, long expected) {
        HistoryPayloadCodec codec = new HistoryPayloadCodec();
        HistoryBackupSupport support = new HistoryBackupSupport(codec, encryption, jdbc);
        String payload =
                codec.write(
                        new HistoryChangeSet(
                                HistoryChangeSet.FORMAT,
                                List.of(
                                        new HistoryChangeSet.Change(
                                                "transactions", 7, Map.of("id", "7"), null)),
                                List.of()));
        when(encryption.plaintext(payload, null)).thenReturn(payload);
        when(jdbc.queryForObject(anyString(), eq(Long.class)))
                .thenReturn(liveMaximum, archiveMaximum);
        Map<Long, Long> transactionIds = new LinkedHashMap<>();

        support.reserve(
                List.of(Map.of("action_state_json", payload)),
                Map.of("transactions", transactionIds),
                null);

        assertThat(transactionIds).containsEntry(7L, expected);
    }
}
