package org.openfinance.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import org.openfinance.dto.OperationHistoryResponse;
import org.openfinance.dto.TransactionResponse;

class ServerTimestampSerializerTest {
    @Test
    void distinguishesUtcHistoryFromServerLocalTimestampsAndCalendarDates() throws Exception {
        TimeZone previous = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Paris"));
            ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
            LocalDateTime timestamp = LocalDateTime.of(2026, 9, 19, 15, 51);
            String history =
                    mapper.readTree(
                                    mapper.writeValueAsString(
                                            OperationHistoryResponse.builder()
                                                    .createdAt(timestamp)
                                                    .build()))
                            .get("createdAt")
                            .asText();
            String transaction =
                    mapper.readTree(
                                    mapper.writeValueAsString(
                                            TransactionResponse.builder()
                                                    .createdAt(timestamp)
                                                    .date(LocalDate.of(2026, 9, 19))
                                                    .build()))
                            .get("createdAt")
                            .asText();
            assertThat(OffsetDateTime.parse(history).toInstant().toString())
                    .isEqualTo("2026-09-19T15:51:00Z");
            assertThat(OffsetDateTime.parse(transaction).toInstant().toString())
                    .isEqualTo("2026-09-19T13:51:00Z");
        } finally {
            TimeZone.setDefault(previous);
        }
    }
}
