package org.openfinance.service.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.service.EncryptedUserDataService;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class HistoryBackupSupportTest {
    @Mock private EncryptedUserDataService encryption;
    @Mock private JdbcTemplate jdbc;

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
