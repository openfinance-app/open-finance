package org.openfinance.service.history;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/** Null columns must be retained, independently of the HTTP mapper's inclusion policy. */
@Component
public class HistoryPayloadCodec {
    private final ObjectMapper mapper = new ObjectMapper();

    public String write(HistoryChangeSet state) {
        try {
            return mapper.writeValueAsString(state);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot capture reversible action", exception);
        }
    }

    public HistoryChangeSet read(String json) {
        try {
            return mapper.readValue(json, HistoryChangeSet.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot read reversible action", exception);
        }
    }
}
