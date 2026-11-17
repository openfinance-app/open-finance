package org.openfinance.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.ImportSourceFile;
import org.openfinance.dto.ImportedTransaction;
import org.openfinance.entity.ImportSession;

/** Serializes each source context without losing decimal precision or source identity. */
@RequiredArgsConstructor
@Slf4j
final class ImportSessionMetadata {
    private final ObjectMapper objectMapper;
    private static final TypeReference<Map<String, Object>> METADATA_TYPE =
            new TypeReference<>() {};
    private static final TypeReference<List<ImportedTransaction>> TRANSACTIONS_TYPE =
            new TypeReference<>() {};

    Map<String, Object> preserveMetadata(String metadata) {
        Map<String, Object> preserved = readMetadataMap(metadata);
        preserved.remove("transactions");
        preserved.remove("count");
        preserved.remove("ledgerBalance");
        preserved.remove("fileCurrency");
        preserved.remove("timestamp");
        return preserved;
    }

    Map<String, Object> deserializeMetadata(String metadata) throws JsonProcessingException {
        // Untyped JSON otherwise becomes Double before convertValue can bind BigDecimal fields.
        // Use a local reader so transactions, splits and opening balances retain source precision.
        return objectMapper
                .readerFor(METADATA_TYPE)
                .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .readValue(metadata);
    }

    Map<String, Object> readMetadataMap(String metadata) {
        if (metadata == null || metadata.trim().isEmpty()) {
            return new HashMap<>();
        }
        try {
            return deserializeMetadata(metadata);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to read import metadata: {}", ex.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * Serialize transactions to JSON string for metadata storage. Uses Jackson ObjectMapper for
     * proper JSON serialization.
     *
     * @param transactions list of imported transactions
     * @param ledgerBalance starting balance from the file
     * @param fileCurrency currency from the file
     * @return JSON string representation
     */
    String serializeTransactions(
            List<ImportedTransaction> transactions, BigDecimal ledgerBalance, String fileCurrency) {
        return serializeTransactions(transactions, ledgerBalance, fileCurrency, Map.of());
    }

    String serializeTransactions(
            List<ImportedTransaction> transactions,
            BigDecimal ledgerBalance,
            String fileCurrency,
            Map<String, Object> extraMetadata) {
        try {
            Map<String, Object> metadata = new HashMap<>();
            if (extraMetadata != null && !extraMetadata.isEmpty()) {
                metadata.putAll(extraMetadata);
            }
            metadata.put("transactions", transactions);
            metadata.put("count", transactions.size());
            metadata.put("ledgerBalance", ledgerBalance);
            metadata.put("fileCurrency", fileCurrency);
            metadata.put("timestamp", Instant.now().toString());
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            log.error("Error serializing transactions: {}", e.getMessage(), e);
            // Fallback to simple JSON
            return String.format(
                    "{\"count\": %d, \"timestamp\": \"%s\", \"error\": \"Serialization failed\"}",
                    transactions.size(), LocalDateTime.now());
        }
    }

    /**
     * Deserialize transactions from JSON metadata. Uses Jackson ObjectMapper for proper JSON
     * deserialization.
     *
     * @param metadata JSON string containing transactions
     * @return list of imported transactions
     */
    List<ImportedTransaction> deserializeTransactions(String metadata) {
        if (metadata == null || metadata.trim().isEmpty()) {
            log.warn("Metadata is empty, returning empty transaction list");
            return new ArrayList<>();
        }

        try {
            Map<String, Object> metadataMap = deserializeMetadata(metadata);

            if (!metadataMap.containsKey("transactions")) {
                log.warn("Metadata does not contain transactions field");
                return new ArrayList<>();
            }

            // Deserialize transactions list
            Object transactionsObj = metadataMap.get("transactions");
            return objectMapper.convertValue(transactionsObj, TRANSACTIONS_TYPE);

        } catch (JsonProcessingException e) {
            log.error("Error deserializing transactions: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    List<ImportSourceFile> sourceFiles(ImportSession session) {
        Object files = readMetadataMap(session.getMetadata()).get("importFiles");
        return files == null
                ? List.of()
                : objectMapper.convertValue(files, new TypeReference<List<ImportSourceFile>>() {});
    }

    String serializeFileMetadata(List<ImportSourceFile> files, Map<String, Object> extra) {
        Map<String, Object> metadata = new HashMap<>(extra);
        metadata.put("importFiles", files);
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not save import files", exception);
        }
    }

    void identifySourceRows(ImportSourceFile file, List<ImportedTransaction> rows) {
        rows.forEach(
                row -> {
                    row.setSourceUploadId(file.getUploadId());
                    row.setSourceFileName(file.getFileName());
                });
    }

    Map<String, List<ImportedTransaction>> partitionSourceRows(
            List<ImportSourceFile> files, List<ImportedTransaction> transactions) {
        Map<String, List<ImportedTransaction>> byFile = new LinkedHashMap<>();
        files.forEach(file -> byFile.put(file.getUploadId(), new ArrayList<>()));
        for (ImportedTransaction transaction : transactions) {
            List<ImportedTransaction> rows = byFile.get(transaction.getSourceUploadId());
            if (rows == null) throw new IllegalArgumentException("Unknown transaction source file");
            rows.add(transaction);
        }
        files.forEach(file -> identifySourceRows(file, byFile.get(file.getUploadId())));
        return byFile;
    }
}
