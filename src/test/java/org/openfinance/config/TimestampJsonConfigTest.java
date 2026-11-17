package org.openfinance.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.openfinance.dto.BackupResponse;
import org.openfinance.dto.OperationHistoryResponse;
import org.openfinance.dto.TransactionResponse;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.http.MockHttpOutputMessage;

class TimestampJsonConfigTest {
    @ParameterizedTest
    @CsvSource({
        "UTC,2026-11-16T00:30:00Z",
        "Europe/Paris,2026-11-15T23:30:00Z",
        "America/New_York,2026-11-16T05:30:00Z"
    })
    void serializesAllHttpTimestampsWithoutChangingCalendarDatesOrArchives(
            String serverZone, String expectedInstant) throws Exception {
        TimeZone previous = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(serverZone));
            ObjectMapper archiveMapper =
                    new ObjectMapper()
                            .registerModule(new JavaTimeModule())
                            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            MappingJackson2HttpMessageConverter converter =
                    new MappingJackson2HttpMessageConverter(archiveMapper);
            new TimestampJsonConfig()
                    .extendMessageConverters(List.of(new StringHttpMessageConverter(), converter));
            LocalDateTime timestamp = LocalDateTime.of(2026, 11, 16, 0, 30);
            Map<String, Object> response =
                    Map.of(
                            "backup", BackupResponse.builder().createdAt(timestamp).build(),
                            "transaction",
                                    TransactionResponse.builder()
                                            .createdAt(timestamp)
                                            .date(LocalDate.of(2026, 11, 16))
                                            .build(),
                            "history",
                                    OperationHistoryResponse.builder().createdAt(timestamp).build(),
                            "timestamp", timestamp);
            MockHttpOutputMessage output = new MockHttpOutputMessage();
            converter.write(response, MediaType.APPLICATION_JSON, output);
            JsonNode json = archiveMapper.readTree(output.getBodyAsBytes());
            assertThat(json.at("/backup/createdAt").asText()).isEqualTo(expectedInstant);
            assertThat(json.at("/transaction/createdAt").asText()).isEqualTo(expectedInstant);
            assertThat(json.at("/timestamp").asText()).isEqualTo(expectedInstant);
            assertThat(json.at("/history/createdAt").asText()).isEqualTo("2026-11-16T00:30:00Z");
            assertThat(json.at("/transaction/date").asText()).isEqualTo("2026-11-16");
            assertThat(archiveMapper.writeValueAsString(timestamp))
                    .isEqualTo("\"2026-11-16T00:30:00\"");
        } finally {
            TimeZone.setDefault(previous);
        }
    }
}
