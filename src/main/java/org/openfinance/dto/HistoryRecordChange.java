package org.openfinance.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;
import org.openfinance.entity.OperationType;

/** Readable, allowlisted details of one record in a captured action. */
public record HistoryRecordChange(
        String recordType,
        String recordId,
        String entityLabel,
        OperationType operationType,
        Map<String, Object> context,
        List<FieldChange> fields) {
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record FieldChange(String field, Object before, Object after) {}
}
